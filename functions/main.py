from firebase_functions import storage_fn

@storage_fn.on_object_finalized(bucket="gloveapp-6793a.firebasestorage.app")
def process_glove_data(event):
    """
    Cloud Function triggered by new file uploads to Firebase Storage.
    Downloads the CSV, runs the analysis, and uploads results to Firestore and Storage.
    """
    # Import and initialize everything inside the function
    import os
    import sys
    import tempfile
    import logging
    from datetime import datetime
    from google.cloud import storage as gcs_storage
    import firebase_admin
    from firebase_admin import firestore

    # Initialize Firebase Admin SDK (only if not already initialized)
    if not firebase_admin._apps:
        firebase_admin.initialize_app()

    # Get Firestore client
    db = firestore.client()

    # Add current directory to Python path
    sys.path.append(os.path.dirname(__file__))

    # Import analysis script
    from script import analyze_glove_data

    # Configure logging
    logger = logging.getLogger(__name__)

    file_name = event.data.name
    bucket_name = event.data.bucket

    logger.info(f"File {file_name} uploaded to bucket {bucket_name}")

    # Only process CSV files in sessions directory
    if not file_name or not file_name.startswith("sessions/") or not file_name.endswith(".csv"):
        logger.info(f"Skipping non-CSV or non-session file: {file_name}")
        return

    # Extract session ID
    session_id = os.path.splitext(os.path.basename(file_name))[0]
    logger.info(f"Processing session ID: {session_id}")

    # Reference to Firestore document
    results_doc_ref = db.collection("processingResults").document(session_id)

    # Get GCS client
    gcs_client = gcs_storage.Client()
    local_csv_path = None

    try:
        # 1. Update Firestore status to 'processing'
        results_doc_ref.set({
            "status": "processing",
            "timestamp": firestore.SERVER_TIMESTAMP,
            "result": "AI algorithm processing...",
            "plots": []
        }, merge=True)
        logger.info(f"Firestore status set to 'processing' for {session_id}")

        # 2. Download the CSV file
        bucket = gcs_client.bucket(bucket_name)
        blob = bucket.blob(file_name)

        with tempfile.NamedTemporaryFile(delete=False, suffix=".csv") as temp_csv_file:
            local_csv_path = temp_csv_file.name
            blob.download_to_filename(local_csv_path)
        logger.info(f"Downloaded {file_name} to {local_csv_path}")

        # 3. Run the analysis
        analysis_results = analyze_glove_data(local_csv_path)
        logger.info(f"Analysis completed for {session_id}")

        # 4. Upload generated plots to Firebase Storage
        plot_urls = []
        for plot_name, plot_bytes in analysis_results['plot_data'].items():
            plot_blob_name = f"session_plots/{session_id}/{plot_name}"
            plot_blob = bucket.blob(plot_blob_name)
            plot_blob.upload_from_string(plot_bytes, content_type="image/png")
            plot_blob.make_public()
            plot_url = plot_blob.public_url
            plot_urls.append(plot_url)
            logger.info(f"Uploaded plot {plot_name} to {plot_url}")

        # 5. Update Firestore with results
        results_doc_ref.update({
            "status": "completed",
            "result": analysis_results['summary_text'],
            "health_scores": analysis_results['health_scores'],
            "plots": plot_urls,
            "processedAt": firestore.SERVER_TIMESTAMP
        })
        logger.info(f"Firestore results updated for {session_id}")

    except Exception as e:
        logger.error(f"Error processing session {session_id}: {e}", exc_info=True)
        results_doc_ref.update({
            "status": "error",
            "result": f"Error processing data: {e}",
            "processedAt": firestore.SERVER_TIMESTAMP
        })
    finally:
        # Clean up temporary file
        if local_csv_path and os.path.exists(local_csv_path):
            os.remove(local_csv_path)
            logger.info(f"Cleaned up temporary file: {local_csv_path}")

    return "Processing completed"