import asyncio
import websockets
import time
import signal
import statistics
import sys

# Configurations
WEBSOCKET_URL = "ws://127.0.0.1:8081/v4/register"
MAX_USERS = 100  # Maximum number of connections
INTERVAL = 0.05  # Time interval between new connections (in seconds)
RECONNECT_DELAY = 10  # Time before attempting reconnection (in seconds)
DISCONNECT_TIME = 600  # Disconnect every 10 minutes (server or client-side)
PING_INTERVAL = 50  # Send a "ping" message every 50 seconds

latency_metrics = []  # Store latencies globally
running_tasks = []  # Store active tasks for cleanup

# Graceful shutdown handling
def handle_exit(sig, frame):
    print("\nStopping WebSocket clients...")

    # Cancel all active tasks
    for task in running_tasks:
        task.cancel()

    if latency_metrics:
        avg_latency = sum(latency_metrics) / len(latency_metrics)
        p99_latency = statistics.quantiles(latency_metrics, n=100)[98]  # 99th percentile
        print(f"\nAverage latency: {avg_latency:.2f} ms")
        print(f"99th percentile latency: {p99_latency:.2f} ms")
    else:
        print("\nNo latency metrics recorded.")

    sys.exit(0)

signal.signal(signal.SIGINT, handle_exit)  # Handle Ctrl+C
signal.signal(signal.SIGTERM, handle_exit)  # Handle termination signals

async def receive_messages(ws, user_id):
    """Handles receiving messages asynchronously."""
    try:
        async for message in ws:
            process_message(user_id, message)
    except websockets.exceptions.ConnectionClosed:
        pass  # Connection closed normally, no need to print an error
    except Exception as e:
        print(f"Error receiving message for user {user_id}: {e}")

async def create_connection(user_id):
    url = f"{WEBSOCKET_URL}/{user_id}"

    while True:
        try:
            async with websockets.connect(url) as ws:
                print(f"User {user_id} connected.")

                disconnect_timer = asyncio.create_task(asyncio.sleep(DISCONNECT_TIME))
                ping_timer = asyncio.create_task(send_pings(ws, user_id))
                recv_task = asyncio.create_task(receive_messages(ws, user_id))  # Start message receiver

                running_tasks.extend([disconnect_timer, ping_timer, recv_task])

                done, pending = await asyncio.wait(
                    [disconnect_timer, recv_task],
                    return_when=asyncio.FIRST_COMPLETED
                )

                # If the disconnect timer completes, disconnect and restart
                if disconnect_timer in done:
                    print(f"User {user_id} timed out after {DISCONNECT_TIME} seconds.")
                    break  # Exit loop to reconnect

        except websockets.exceptions.ConnectionClosed:
            print(f"User {user_id} disconnected unexpectedly. Reconnecting...")

        except Exception as e:
            print(f"User {user_id} failed to connect: {e}")

        await asyncio.sleep(RECONNECT_DELAY)  # Wait before reconnecting

async def send_pings(ws, user_id):
    """Sends a 'ping' message every 50 seconds."""
    try:
        while True:
            await asyncio.sleep(PING_INTERVAL)
            await ws.send("ping")
    except websockets.exceptions.ConnectionClosed:
        pass  # Stop pinging if the connection is closed
    except Exception as e:
        print(f"Error sending ping for user {user_id}: {e}")

def process_message(user_id, message):
    """Process received message and calculate latency."""
    try:
        parts = message.split(":")
        if len(parts) != 3:
            print(f"Invalid message format: {message}")
            return

        received_user_id, msg, publish_time = parts
        if int(received_user_id) != user_id:
            print(f"RED FLAG - {message}")
            sys.exit(1)

        publish_time = float(publish_time)
        latency = (time.time() * 1000) - publish_time  # Convert to ms
        latency_metrics.append(latency)
        print(f"User {user_id} latency: {latency:.2f} ms")

    except Exception as e:
        print(f"Error processing message: {message}, Error: {e}")

async def start_connections():
    tasks = []
    for user_id in range(MAX_USERS):
        task = asyncio.create_task(create_connection(user_id))
        tasks.append(task)
        running_tasks.append(task)
        await asyncio.sleep(INTERVAL)  # Stagger connection starts
    await asyncio.gather(*tasks)

if __name__ == "__main__":
    print("Starting WebSocket connections incrementally...")
    asyncio.run(start_connections())
