import asyncio
import websockets

# Configurations
WEBSOCKET_URL = "ws://127.0.0.1:8081/v4/register"
MAX_USERS = 5000  # Maximum number of connections
INTERVAL = 0.1  # Time interval between new connections (in seconds)

# WebSocket Connector
async def create_connection(user_id):
    url = f"{WEBSOCKET_URL}/{user_id}"
    try:
        ws = await websockets.connect(url)
        print(f"Connected: User {user_id}")
        await asyncio.Future()  # Keep the connection open indefinitely
    except Exception as e:
        print(f"Error connecting WebSocket for {user_id}: {e}")

# Incrementally start WebSocket connections
async def start_connections():
    tasks = []
    for user_id in range(MAX_USERS):
        task = asyncio.create_task(create_connection(user_id))
        tasks.append(task)
        await asyncio.sleep(INTERVAL)  # Wait before creating the next connection
    await asyncio.gather(*tasks)

if __name__ == "__main__":
    print("Starting WebSocket connections incrementally...")
    asyncio.run(start_connections())
