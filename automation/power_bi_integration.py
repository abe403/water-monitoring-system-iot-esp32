import requests
import json
import time
import random
import logging
from datetime import datetime

logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(levelname)s - %(message)s')

# Mock Power BI Push Dataset API Endpoint
# In a real enterprise setup, this URL is provided by the Power BI service.
POWER_BI_REST_API_URL = "https://api.powerbi.com/beta/workspace/datasets/dataset_id/rows?key=MOCK_KEY"

def stream_telemetry_to_powerbi(sensor_id="ESP32_TANK_01", batch_size=10, interval_seconds=1):
    """
    Simulates streaming real-time IoT sensor telemetry to a Power BI Push Dataset.
    This enables dynamic Power BI dashboards for advanced visualization and proactive monitoring.
    """
    logging.info(f"Starting Power BI Telemetry Stream for {sensor_id}...")
    
    for i in range(batch_size):
        # Generate mock telemetry data
        water_level = max(0, 100 - (i * 2) + random.uniform(-1, 1)) # Gradually decreasing with noise
        flow_rate = random.uniform(5.0, 12.0)
        temperature = random.uniform(18.0, 22.0)
        
        # Prepare payload according to Power BI Push Dataset schema
        payload = [{
            "timestamp": datetime.utcnow().isoformat() + "Z",
            "sensor_id": sensor_id,
            "water_level_pct": round(water_level, 2),
            "flow_rate_lpm": round(flow_rate, 2),
            "temperature_c": round(temperature, 2),
            "status": "Normal" if water_level > 20 else "Critical"
        }]
        
        # In a real environment, we use requests.post
        # response = requests.post(POWER_BI_REST_API_URL, data=json.dumps(payload))
        
        # Mocking the successful API response
        logging.info(f"Pushed to Power BI Dashboard: {json.dumps(payload)}")
        
        time.sleep(interval_seconds)

    logging.info("Completed telemetry stream batch to Power BI.")

if __name__ == "__main__":
    # Example execution
    stream_telemetry_to_powerbi(batch_size=5)
