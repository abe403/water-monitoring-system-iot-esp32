import requests
import time
from selenium import webdriver
from selenium.webdriver.common.by import By
from selenium.webdriver.support.ui import WebDriverWait

# CONFIGURATION
HA_URL = "http://localhost:8123"
# PASTE YOUR TOKEN HERE
ACCESS_TOKEN = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJhYmFiOTRmOGQwM2Y0MTVhOGI2OTAyMDg4NGRjMjJjMCIsImlhdCI6MTc3NzM3NTE4MiwiZXhwIjoyMDkyNzM1MTgyfQ.QQ5wMRKDwLVs8yzjx2BvZFUzs3sgXt4vEqcCO7FN3vI" 

headers = {
    "Authorization": f"Bearer {ACCESS_TOKEN}",
    "content-type": "application/json",
}

def update_real_sensor(value):
    """Simulates the ESP32 sending data to the Real Home Assistant API"""
    url = f"{HA_URL}/api/states/sensor.water_level"
    payload = {
        "state": str(value),
        "attributes": {
            "unit_of_measurement": "%",
            "friendly_name": "Tank Level"
        }
    }
    response = requests.post(url, headers=headers, json=payload)
    if response.status_code in [200, 201]:
        print(f"Successfully injected {value}% into Real Home Assistant!")
    else:
        print(f"Error: {response.status_code} - {response.text}")

def test_real_dashboard():
    """Selenium script to verify the Real Home Assistant UI"""
    driver = webdriver.Chrome()
    driver.get(HA_URL)
    
    print("Please login to Home Assistant manually if prompted...")
    
    # Wait for the dashboard to load and check the sensor
    # Note: You might need to add the 'sensor.water_level' to your dashboard first
    time.sleep(10) 
    
    try:
        # Update the value via API
        update_real_sensor(45)
        time.sleep(2) # Give the UI a second to refresh
        
        # In HA, the value is often inside an 'ha-state-label-badge' or a 'simple-entity-card'
        # This is a generic way to find the text on the page
        page_text = driver.find_element(By.TAG_NAME, "body").text
        if "45" in page_text:
            print("VERIFIED: Real Home Assistant is displaying the IoT data!")
        else:
            print("Failed: Value not found on dashboard.")
            
    finally:
        driver.quit()

if __name__ == "__main__":
    update_real_sensor(82) # Initial injection
    test_real_dashboard() # Full UI test
