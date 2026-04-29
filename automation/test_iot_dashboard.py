import os
import time
from selenium import webdriver
from selenium.webdriver.common.by import By
from selenium.webdriver.support.ui import WebDriverWait
from selenium.webdriver.support import expected_conditions as EC

def test_water_level_monitoring():
    # 1. Setup - Using Chrome in headless mode for CI/CD environments
    options = webdriver.ChromeOptions()
    # options.add_argument("--headless") # Uncomment for headless CI
    driver = webdriver.Chrome(options=options)

    try:
        # 2. Navigate to the Home Assistant Mock Dashboard
        # In a real scenario, this would be http://localhost:8123
        file_path = "file://" + os.path.abspath("iot_dashboard_mock.html")
        driver.get(file_path)

        print("Navigated to Dashboard. Checking initial state...")

        # 3. Verify Initial State (Tank should be at 100%)
        level_element = driver.find_element(By.ID, "sensor-level-value")
        assert level_element.text == "100", f"Expected 100%, but got {level_element.text}"
        print("Initial state verified: 100%")

        # 4. Wait for the API to update the sensor (Simulated delay)
        print("Waiting for telemetry update...")
        WebDriverWait(driver, 10).until(
            lambda d: d.find_element(By.ID, "sensor-level-value").text != "100"
        )

        # 5. Verify the Update (Should be 85% as per our mock script)
        new_level = driver.find_element(By.ID, "sensor-level-value").text
        print(f"Telemetry received: {new_level}%")
        assert new_level == "85", f"Expected 85%, but got {new_level}"

        # 6. Simulate a Critical Leak and Verify Alert
        print("Simulating critical leak...")
        driver.execute_script("updateSensor(15)") # Directly calling the JS function to simulate API injection
        
        # Verify the Alert Banner appears
        alert = WebDriverWait(driver, 5).until(
            EC.visibility_of_element_located((By.ID, "status-alert"))
        )
        assert "LEAK DETECTED" in alert.text
        print("Success: Alert system correctly identified the leak!")

    finally:
        # 7. Cleanup
        time.sleep(2)
        driver.quit()

if __name__ == "__main__":
    test_water_level_monitoring()
