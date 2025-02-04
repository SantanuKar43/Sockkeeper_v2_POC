import asyncio
import websockets
import requests
import json
import random
import time
from concurrent.futures import ThreadPoolExecutor
from confluent_kafka.admin import AdminClient, NewTopic

# Configurations
KAFKA_BOOTSTRAP_SERVERS = "localhost:9092"  # Change as needed
BASE_URL = "http://localhost:8888"  # Change as needed
WEBSOCKET_URL = "ws://localhost:8888/v3/register"
NUM_USERS = 500
MESSAGES_PER_USER = 50

# Kafka Admin Client for topic creation
admin_client = AdminClient({"bootstrap.servers": KAFKA_BOOTSTRAP_SERVERS})


# HTTP Publisher
def publish_message(user_id, message):
    """Publishes messages via HTTP."""
    url = f"{BASE_URL}/v3/publish/{user_id}"
    try:
        response = requests.post(url, data=message)
        if response.status_code != 204:
            print(f"Failed to publish for user {user_id}: {response.text}")
    except Exception as e:
        print(f"Error publishing for {user_id}: {e}")


# Run HTTP Publishers
def run_http_publishers():
    with ThreadPoolExecutor(max_workers=50) as executor:
        for user_id in range(NUM_USERS):
            for _ in range(MESSAGES_PER_USER):
                message = json.dumps({"user_id": user_id, "message": f"Hello {random.randint(1, 100)}"})
                executor.submit(publish_message, user_id, message)
        executor.shutdown(wait=True)

if __name__ == "__main__":
    print("Starting Load Test...")
    start_time = time.time()

    print("Publishing messages...")
    run_http_publishers()

    print(f"Test completed in {time.time() - start_time:.2f} seconds.")
