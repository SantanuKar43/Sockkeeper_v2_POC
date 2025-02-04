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
MESSAGES_PER_USER = 10

# Kafka Admin Client for topic creation
admin_client = AdminClient({"bootstrap.servers": KAFKA_BOOTSTRAP_SERVERS})

def create_kafka_topics():
    """Creates 1000 Kafka topics (one per user)."""
    topics = [NewTopic(f"topic-{user_id}", num_partitions=1, replication_factor=1) for user_id in range(NUM_USERS)]

    print("Creating Kafka topics...")
    futures = admin_client.create_topics(topics)

    for topic, future in futures.items():
        try:
            future.result()  # Wait for topic creation
            print(f"Created topic: {topic}")
        except Exception as e:
            print(f"Failed to create topic {topic}: {e}")


# WebSocket Consumer
async def consume_messages(user_id):
    url = f"{WEBSOCKET_URL}/{user_id}"
    try:
        async with websockets.connect(url) as ws:
            print(f"Connected: User {user_id}")
            start_time = time.time()
            while time.time() - start_time < 60:  # Timeout after 25s
                try:
                    msg = await asyncio.wait_for(ws.recv(), timeout=30)
                    print(f"User {user_id} received: {msg}")
                    start_time = time.time()  # Reset timeout on message
                except asyncio.TimeoutError:
                    print(f"User {user_id} timed out, closing connection.")
                    break
    except Exception as e:
        print(f"Error connecting WebSocket for {user_id}: {e}")

# Run WebSocket listeners
async def run_websockets():
    tasks = [consume_messages(user_id) for user_id in range(NUM_USERS)]
    await asyncio.gather(*tasks)


if __name__ == "__main__":
    print("Starting Load Test...")
    start_time = time.time()

    create_kafka_topics()

    print("Listening for messages via WebSockets...")
    asyncio.run(run_websockets())

    print(f"Test completed in {time.time() - start_time:.2f} seconds.")
