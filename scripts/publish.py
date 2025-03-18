import time
import signal
import random
import requests
from concurrent.futures import ThreadPoolExecutor, as_completed
import numpy as np

# Configurations
BASE_URL = "http://localhost:8081"
NUM_USERS = 1000
PUBLISH_RATE = 200  # Messages per second
MAX_WORKERS = 200   # Allow enough workers for full rate

# Metrics
latencies = []
total_messages = 0
running = True
executor = None  # Global executor to shut down on Ctrl+C

# Signal handler to stop publishing gracefully
def signal_handler(sig, frame):
    global running, executor
    print("\nStopping load test...")
    running = False  # Stop new tasks
    if executor:
        executor.shutdown(wait=False)  # Force shutdown immediately

def publish_message(user_id):
    """Publishes messages via HTTP."""
    global total_messages
    if not running:  # Skip execution if stopping
        return

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
    """Runs the publisher at a fixed rate of 200 messages/sec."""
    global executor
    executor = ThreadPoolExecutor(max_workers=MAX_WORKERS)

    try:
        while running:
            start_time = time.time()

            # Submit exactly 200 requests
            futures = [executor.submit(publish_message, random.randint(0, NUM_USERS - 1)) for _ in range(PUBLISH_RATE)]

            # Wait for all requests to complete (or exit early if stopping)
            for future in as_completed(futures):
                if not running:
                    break  # Stop waiting if we received Ctrl+C

            # Ensure 200 requests per second
            elapsed_time = time.time() - start_time
            sleep_time = max(0, 1 - elapsed_time)
            time.sleep(sleep_time)

    except KeyboardInterrupt:
        print("\nReceived Ctrl+C. Shutting down immediately.")
    finally:
        executor.shutdown(wait=False)  # Force shutdown

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
