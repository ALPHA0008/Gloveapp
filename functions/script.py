import numpy as np
import pandas as pd
import matplotlib.pyplot as plt
from scipy.signal import find_peaks
from scipy.interpolate import interp1d
import os
import io # To save plots to a byte stream
import logging # Add logging for better debugging in Cloud Functions
import matplotlib.patches as mpatches # Import for legend patches

logger = logging.getLogger(__name__)
# Set logger level to DEBUG to see all messages
logger.setLevel(logging.DEBUG)

def moving_average(data, window_size=11):
    """Applies a simple moving average filter to the data."""
    logger.debug(f"moving_average: Input data type: {type(data)}, dtype: {getattr(data, 'dtype', 'N/A')}, shape: {getattr(data, 'shape', 'N/A')}")
    logger.debug(f"moving_average: Input data sample (first 5): {data[:5] if hasattr(data, '__len__') and len(data) > 0 else 'Empty'}")

    # Ensure data is numpy array and numeric before convolution
    # This line is where the error occurs, so let's be extra careful here.
    try:
        data = np.asarray(data, dtype=float)
        logger.debug(f"moving_average: Data after np.asarray conversion: {type(data)}, dtype: {data.dtype}, shape: {data.shape}")
    except Exception as e:
        logger.error(f"moving_average: Error converting data to float array: {e}", exc_info=True)
        raise # Re-raise to see full traceback in logs

    # Handle cases where data might be too short after conversion
    if len(data) < window_size:
        logger.warning(f"moving_average: Data length ({len(data)}) is less than window_size ({window_size}). Returning original data.")
        return data # Or handle as appropriate, e.g., return NaNs

    return np.convolve(data, np.ones(window_size)/window_size, mode='same')

def peak_trough_envelope(signal, prominence, distance=100):
    """
    Calculates upper and lower envelopes based on peaks and troughs.

    Args:
        signal (np.array): The input 1D signal.
        prominence (float): Required prominence for peak/trough detection.
        distance (int): Required minimum horizontal distance between adjacent peaks/troughs.

    Returns:
        tuple: (upper_envelope, lower_envelope, peak_indices, trough_indices)
    """
    # Ensure signal is numeric and handle NaNs before peak finding
    signal = np.asarray(signal, dtype=float)
    signal = signal[~np.isnan(signal)] # Remove NaNs for peak finding

    # Handle cases where signal might be too short or all NaNs after cleaning
    if len(signal) < 2:
        x_indices = np.arange(len(signal))
        return np.full_like(x_indices, np.nan), np.full_like(x_indices, np.nan), np.array([]), np.array([])

    peaks, _ = find_peaks(signal, prominence=prominence, distance=distance)
    troughs, _ = find_peaks(-signal, prominence=prominence, distance=distance) # Find troughs by inverting signal

    x_indices = np.arange(len(signal))

    # Ensure envelopes cover the full signal range by including start/end points
    # Handle cases where peaks/troughs might be empty
    all_x_peaks = np.concatenate(([0], peaks, [len(signal)-1]))
    all_y_peaks = np.concatenate(([signal[0]], signal[peaks], [signal[-1]]))
    if len(all_x_peaks) < 2: # Fallback if not enough points for interpolation
        all_x_peaks = np.array([0, len(signal)-1])
        all_y_peaks = np.array([signal[0], signal[-1]])

    all_x_troughs = np.concatenate(([0], troughs, [len(signal)-1]))
    all_y_troughs = np.concatenate(([signal[0]], signal[troughs], [signal[-1]]))
    if len(all_x_troughs) < 2: # Fallback if not enough points for interpolation
        all_x_troughs = np.array([0, len(signal)-1])
        all_y_troughs = np.array([signal[0], signal[-1]])


    # Interpolate to create continuous envelopes
    upper_env = interp1d(all_x_peaks, all_y_peaks, kind='linear', bounds_error=False,
                         fill_value=(all_y_peaks[0], all_y_peaks[-1]))
    lower_env = interp1d(all_x_troughs, all_y_troughs, kind='linear', bounds_error=False,
                         fill_value=(all_y_troughs[0], all_y_troughs[-1]))

    return upper_env(x_indices), lower_env(x_indices), peaks, troughs

def analyze_glove_data(csv_file_path):
    """
    Analyzes glove sensor data from a CSV file, calculates health scores,
    and generates plots.

    Args:
        csv_file_path (str): The path to the input CSV file.

    Returns:
        dict: A dictionary containing:
            - 'health_scores': A list of health scores for each finger.
            - 'plot_data': A dictionary where keys are plot names and values are
                           bytes of the PNG images.
            - 'summary_text': A string summarizing the analysis.
    """
    # Thresholds (difference between max - min) for healthy classification
    healthy_thresholds = [28.055, 195.985, 164.52, 177.4, 91.63]
    finger_names = ['Thumb', 'Index', 'Middle', 'Ring', 'Little']

    try:
        df = pd.read_csv(csv_file_path, header=None)
        # Consider only the first 1000 samples
        df = df.head(1000)

        logger.debug(f"Original DataFrame head:\n{df.head()}")
        logger.debug(f"Original DataFrame dtypes:\n{df.dtypes}")

        # --- CRITICAL FIX: Convert columns to numeric, coercing errors ---
        # Assuming flex data is columns 1-5 (0-indexed)
        # Use pd.to_numeric with errors='coerce' to turn non-numeric values into NaN
        flex_cols = df.columns[1:6]
        for col_idx in flex_cols: # Iterate over column labels/indices
            df[col_idx] = pd.to_numeric(df[col_idx], errors='coerce')
        logger.debug(f"DataFrame dtypes after pd.to_numeric:\n{df.dtypes}")

        # Drop rows that contain any NaN values in the flex data columns after conversion
        # This ensures all values in flex_data are indeed numbers
        df.dropna(subset=flex_cols, inplace=True)
        logger.debug(f"DataFrame head after dropna:\n{df.head()}")
        logger.debug(f"DataFrame shape after dropna: {df.shape}")


        if df.empty:
            logger.error("CSV file is empty or contains no valid numeric data after cleaning.")
            return {
                'health_scores': [],
                'plot_data': {},
                'summary_text': "Error: CSV file is empty or contains no valid numeric data after cleaning."
            }

        flex_data = df.iloc[:, 1:6].to_numpy()
        logger.debug(f"flex_data type: {type(flex_data)}, dtype: {flex_data.dtype}, shape: {flex_data.shape}")
        logger.debug(f"flex_data sample (first 5 rows):\n{flex_data[:5]}")

    except Exception as e:
        logger.error(f"Error reading CSV or parsing flex data: {e}", exc_info=True)
        return {
            'health_scores': [],
            'plot_data': {},
            'summary_text': f"Error: Could not process CSV file. {e}"
        }

    # If flex_data is empty after cleaning, return early
    if flex_data.size == 0:
        logger.warning("No valid flex data found after processing CSV.")
        return {
            'health_scores': [],
            'plot_data': {},
            'summary_text': "Warning: No valid flex data found in CSV for analysis."
        }


    filtered_flex = np.apply_along_axis(moving_average, axis=0, arr=flex_data)

    actual_diffs = []
    health_scores = []
    plot_data = {}
    summary_lines = ["Finger Healthiness Analysis:"]

    for i in range(5):
        raw_signal = flex_data[:, i]
        # Ensure signal is numeric and interpolate NaNs
        signal = pd.Series(filtered_flex[:, i]).interpolate(limit_direction='both').to_numpy()

        # Handle cases where signal might be too short or all NaNs after conversion
        if len(signal) < 2 or np.all(np.isnan(signal)):
            summary_lines.append(f"{finger_names[i]} Finger: Not enough valid data points for analysis.")
            actual_diffs.append(0)
            health_scores.append(0)
            continue

        # Adjust prominence dynamically based on signal range, not just max*0.05
        # Use a more robust prominence calculation, e.g., a fraction of the signal's range
        signal_range = np.nanmax(signal) - np.nanmin(signal)
        prominence_val = signal_range * 0.1 # 10% of the signal range for prominence
        if np.isnan(prominence_val) or prominence_val <= 0:
            prominence_val = np.std(signal) * 0.5 # Fallback to std dev if range is problematic

        # Ensure distance is at least 1 for very short signals
        distance_val = max(1, len(signal) // 10) # At least 1, up to 1/10th of signal length

        upper_env, lower_env, peaks, troughs = peak_trough_envelope(
            signal, prominence=prominence_val, distance=distance_val
        )

        max_thresh = np.mean(signal[peaks]) if len(peaks) > 0 else np.nan
        min_thresh = np.mean(signal[troughs]) if len(troughs) > 0 else np.nan

        threshold_diff = 0
        status = "Analysis Incomplete"
        score = 0

        if not np.isnan(max_thresh) and not np.isnan(min_thresh):
            threshold_diff = max_thresh - min_thresh
            healthy_limit = healthy_thresholds[i]
            is_healthy = threshold_diff >= healthy_limit
            status = "Healthy" if is_healthy else "Not Healthy"
            score = max(0, min(100, (threshold_diff / healthy_limit) * 100))
        else:
            status = "Not Enough Peaks/Troughs"
            score = 0 # Default score if analysis not possible

        actual_diffs.append(threshold_diff)
        healthy_limit = healthy_thresholds[i] # Re-assign healthy_limit for clarity in score calculation
        score = max(0, min(100, (threshold_diff / healthy_limit) * 100)) # Re-calculate score here
        health_scores.append(score)

        if status != "Not Enough Peaks/Troughs":
            summary_lines.append(f"- {finger_names[i]}: Diff={threshold_diff:.2f}, Limit={healthy_limit:.2f} → {status} (Score: {score:.1f})")
        else:
            summary_lines.append(f"- {finger_names[i]}: Could not calculate thresholds → {status} (Score: {score:.1f})")

        # Plotting individual finger analysis
        fig, ax = plt.subplots(figsize=(12, 5))
        ax.plot(raw_signal, color='lightgray', alpha=0.6, linewidth=0.8, label="Raw Signal")
        ax.plot(signal, color='blue', linewidth=1.2, label="Filtered Signal")
        # Only plot envelopes if they are not all NaNs
        if not np.all(np.isnan(upper_env)):
            ax.plot(upper_env, 'g-', linewidth=2, label="Upper Envelope")
        if not np.all(np.isnan(lower_env)):
            ax.plot(lower_env, 'r-', linewidth=2, label="Lower Envelope")

        ax.scatter(peaks, signal[peaks], color='g', s=50, zorder=5)
        ax.scatter(troughs, signal[troughs], color='r', s=50, zorder=5)

        if not np.isnan(max_thresh):
            ax.axhline(max_thresh, color='purple', linestyle='--', linewidth=1.2,
                        label=f'Avg Peak = {max_thresh:.2f}')
        if not np.isnan(min_thresh):
            ax.axhline(min_thresh, color='brown', linestyle='--', linewidth=1.2,
                        label=f'Avg Trough = {min_thresh:.2f}')

        # Add status box
        status_color = 'lightgreen' if status == "Healthy" else 'lightcoral'
        ax.text(0.02, 0.95, status,
                 transform=ax.transAxes,
                 fontsize=14, fontweight='bold',
                 bbox=dict(facecolor=status_color, edgecolor='black', boxstyle='round,pad=0.4'))

        ax.set_title(f'{finger_names[i]} Finger Health Analysis', fontsize=14)
        ax.set_xlabel("Sample Index", fontsize=12)
        ax.set_ylabel("Signal Value", fontsize=12)
        ax.legend(loc='best')
        ax.grid(True, alpha=0.3)
        fig.tight_layout()

        # Save plot to in-memory buffer
        buf = io.BytesIO()
        fig.savefig(buf, format='png', dpi=300)
        buf.seek(0) # Rewind buffer to the beginning
        plot_data[f"flex_finger_{i+1}_analysis.png"] = buf.getvalue()
        plt.close(fig) # Close the plot to free memory

    # === Bar Graph 1: Actual vs Healthy Threshold Diffs ===
    fig2, ax2 = plt.subplots(figsize=(10, 6))
    x_indices = np.arange(len(finger_names))
    width = 0.35

    bars1 = ax2.bar(x_indices - width/2, actual_diffs, width, label='Actual', color='royalblue')
    bars2 = ax2.bar(x_indices + width/2, healthy_thresholds, width, label='Healthy Threshold', color='limegreen')

    ax2.set_xticks(x_indices)
    ax2.set_xticklabels(finger_names)
    ax2.set_ylabel('Threshold Difference')
    ax2.set_title('Actual vs Healthy Threshold Differences per Finger')
    ax2.legend()
    ax2.grid(True, alpha=0.3)

    # Annotate bars
    for bar in bars1 + bars2:
        height = bar.get_height()
        ax2.annotate(f'{height:.1f}',
                     xy=(bar.get_x() + bar.get_width() / 2, height),
                     xytext=(0, 3), # 3 points vertical offset
                     textcoords="offset points",
                     ha='center', va='bottom', fontsize=9)

    fig2.tight_layout()
    buf = io.BytesIO()
    fig2.savefig(buf, format='png', dpi=300)
    buf.seek(0)
    plot_data["threshold_comparison_bar_chart.png"] = buf.getvalue()
    plt.close(fig2) # Close the plot to free memory

    # === Bar Graph 2: Healthiness Scores ===
    fig3, ax3 = plt.subplots(figsize=(10, 5))

    # Define colors based on score
    score_colors = []
    for score_val in health_scores:
        if score_val >= 100:
            score_colors.append('forestgreen')
        elif score_val >= 80:
            score_colors.append('gold')
        else:
            score_colors.append('indianred')

    bars = ax3.bar(finger_names, health_scores, color=score_colors)
    ax3.set_ylim(0, 110) # Set y-axis limit slightly above 100 for visual clarity
    ax3.set_ylabel("Healthiness Score (0–100)")
    ax3.set_title("Finger Healthiness Score")

    # Annotate score bars
    for bar, score_val in zip(bars, health_scores):
        height = bar.get_height()
        ax3.annotate(f'{score_val:.1f}',
                     xy=(bar.get_x() + bar.get_width() / 2, height),
                     xytext=(0, 3), # 3 points vertical offset
                     textcoords="offset points",
                     ha='center', va='bottom', fontsize=9)

    # Add custom legend for score ranges
    green_patch = mpatches.Patch(color='forestgreen', label='Healthy (100)')
    yellow_patch = mpatches.Patch(color='gold', label='Moderate (80–99)')
    red_patch = mpatches.Patch(color='indianred', label='Unhealthy (<80)') # Corrected from Patches to Patch
    ax3.legend(handles=[green_patch, yellow_patch, red_patch], loc='lower right')

    ax3.grid(True, alpha=0.3)
    fig3.tight_layout()
    buf = io.BytesIO()
    fig3.savefig(buf, format='png', dpi=300)
    buf.seek(0)
    plot_data["healthiness_score_bar_chart.png"] = buf.getvalue()
    plt.close(fig3) # Close the plot to free memory

    # Final summary text
    final_summary = "\n".join(summary_lines)
    final_summary += "\n\nOverall Healthiness Scores (0-100):"
    for i in range(5):
        final_summary += f"\n- {finger_names[i]} Finger: {health_scores[i]:.2f}"

    return {
        'health_scores': health_scores,
        'plot_data': plot_data,
        'summary_text': final_summary
    }

# Example usage (for local testing, not used in Cloud Function directly)
if __name__ == "__main__":
    # Create a dummy CSV file for testing
    dummy_data = {
        'Timestamp': range(100),
        'Flex1': np.random.rand(100) * 50 + 100,
        'Flex2': np.random.rand(100) * 50 + 150,
        'Flex3': np.random.rand(100) * 50 + 120,
        'Flex4': np.random.rand(100) * 50 + 130,
        'Flex5': np.random.rand(100) * 50 + 90,
        'FSR1': np.random.rand(100) * 100 + 500,
        'FSR2': np.random.rand(100) * 100 + 600,
        'FSR3': np.random.rand(100) * 100 + 550,
        'FSR4': np.random.rand(100) * 100 + 620,
        'FSR5': np.random.rand(100) * 100 + 580,
        'IMU_X': np.random.rand(100) * 20 - 10,
        'IMU_Y': np.random.rand(100) * 20 - 10,
        'IMU_Z': np.random.rand(100) * 20 - 10,
        'IMU_Roll': np.random.rand(100) * 20 - 10,
        'IMU_Pitch': np.random.rand(100) * 20 - 10,
        'IMU_Yaw': np.random.rand(100) * 20 - 10,
        'BioAmp': np.random.rand(100) * 50 + 200
    }
    dummy_df = pd.DataFrame(dummy_data)
    dummy_csv_path = "dummy_glove_data.csv"
    dummy_df.to_csv(dummy_csv_path, header=False, index=False) # header=False as per your app's CSV saving

    print(f"Dummy CSV created at: {dummy_csv_path}")

    results = analyze_glove_data(dummy_csv_path)
    print("\n--- Analysis Results ---")
    print(results['summary_text'])
    print("\nHealth Scores:", results['health_scores'])

    # To check saved plots locally (optional, for debugging)
    # for plot_name, plot_bytes in results['plot_data'].items():
    #     with open(f"local_plot_{plot_name}", "wb") as f:
    #         f.write(plot_bytes)
    #     print(f"Saved local plot: local_plot_{plot_name}")

    os.remove(dummy_csv_path) # Clean up dummy file
