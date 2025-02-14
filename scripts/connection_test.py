import asyncio
import websockets
import time

# Configurations
WEBSOCKET_URL = "ws://sockkeeper.local/v4/register"
MAX_USERS = 100  # Maximum number of connections
INTERVAL = 0.1  # Time interval between new connections (in seconds)

# WebSocket Consumer
async def consume_messages(user_id):
    url = f"{WEBSOCKET_URL}/{user_id}"
    try:
        async with websockets.connect(url) as ws:
            print(f"Connected: User {user_id}")
            while True:
                try:
                    msg = await ws.recv()
                    print(f"User {user_id} received: {msg}")
                except Exception as e:
                    print(f"Error receiving message for {user_id}: {e}")
                    break
    except Exception as e:
        print(f"Error connecting WebSocket for {user_id}: {e}")

# Incrementally start WebSocket connections
async def start_connections():
    tasks = []
    for user_id in range(MAX_USERS):
        task = asyncio.create_task(consume_messages(user_id))
        tasks.append(task)
        await asyncio.sleep(INTERVAL)  # Wait 100ms before creating the next connection
    await asyncio.gather(*tasks)  # Wait for all connections

if __name__ == "__main__":
    print("Starting WebSocket connections incrementally...")
    asyncio.run(start_connections())