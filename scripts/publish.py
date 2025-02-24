import asyncio
import requests
import json
import random
import time
import signal
from concurrent.futures import ThreadPoolExecutor
import numpy as np

# Configurations
BASE_URL = "http://localhost:8081"  # Change as needed
NUM_USERS = 100  # Random selection among 100 users
PUBLISH_RATE = 600  # Messages per second

# Metrics
latencies = []
total_messages = 0
running = True

# Signal handler to stop publishing gracefully
def signal_handler(sig, frame):
    global running
    running = False
    print("\nStopping load test...")

def publish_message(user_id):
    """Publishes messages via HTTP."""
    global total_messages
    url = f"{BASE_URL}/v4/publish/{user_id}"
    message = f"{user_id}:{random.randint(1, 100)}:{time.time() * 1000}"

    start_time = time.time()
    try:
        response = requests.post(url, data=message)
        latency = (time.time() - start_time) * 1000  # Convert to milliseconds
        latencies.append(latency)

        if response.status_code != 204:
            print(f"Failed to publish for user {user_id}: {response.text}")
    except Exception as e:
        print(f"Error publishing for {user_id}: {e}")
    finally:
        total_messages += 1

def run_http_publishers():
    with ThreadPoolExecutor(max_workers=100) as executor:
        while running:
            start_time = time.time()
            futures = [executor.submit(publish_message, random.randint(0, NUM_USERS - 1)) for _ in range(PUBLISH_RATE)]

            # Wait for the next batch while maintaining the publish rate
            elapsed_time = time.time() - start_time
            sleep_time = max(0, 1 - elapsed_time)
            time.sleep(sleep_time)
        executor.shutdown(wait=False)

if __name__ == "__main__":
    print("Starting Load Test... Press Ctrl+C to stop.")
    signal.signal(signal.SIGINT, signal_handler)

    start_time = time.time()
    run_http_publishers()

    # Metrics Calculation
    duration = time.time() - start_time
    avg_latency = np.mean(latencies) if latencies else 0
    p99_latency = np.percentile(latencies, 99) if latencies else 0

    print(f"Total messages published: {total_messages}")
    print(f"Test duration: {duration:.2f} seconds")
    print(f"Average latency: {avg_latency:.2f} ms")
    print(f"99th percentile latency: {p99_latency:.2f} ms")
