#!/bin/bash

echo "======================================"
echo "Reminder Task Test Script"
echo "======================================"
echo ""

SERVER_URL="http://localhost:8080"

echo "1. Testing server health..."
curl -s -X POST "$SERVER_URL" \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": 1,
    "method": "ping"
  }' | jq .
echo ""
echo ""

echo "2. Getting available tools..."
curl -s -X POST "$SERVER_URL" \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": 2,
    "method": "tools/list"
  }' | jq .
echo ""
echo ""

echo "3. Scheduling a reminder for 1 minute..."
curl -s -X POST "$SERVER_URL" \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": 3,
    "method": "schedule_reminder",
    "params": {
      "delayMinutes": 1,
      "message": "нужно поесть"
    }
  }' | jq .
echo ""
echo ""

echo "4. Getting pending reminders..."
sleep 2
curl -s -X POST "$SERVER_URL" \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": 4,
    "method": "get_pending_reminders"
  }' | jq .
echo ""
echo ""

echo "5. Waiting for reminder to execute (65 seconds)..."
sleep 65

echo "6. Getting pending reminders after execution..."
curl -s -X POST "$SERVER_URL" \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": 6,
    "method": "get_pending_reminders"
  }' | jq .
echo ""
echo ""

echo "7. Adding fitness logs for the last 7 days..."
curl -s -X POST "$SERVER_URL" \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": 7,
    "method": "add_fitness_log",
    "params": {
      "date": "2026-04-01",
      "weight": 83.5,
      "calories": 2500,
      "protein": 160,
      "workoutCompleted": true,
      "steps": 8500,
      "sleepHours": 7.5,
      "notes": "Тренировка ног, самочувствие отличное"
    }
  }' | jq .
echo ""
echo ""

curl -s -X POST "$SERVER_URL" \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": 8,
    "method": "add_fitness_log",
    "params": {
      "date": "2026-04-02",
      "weight": 83.3,
      "calories": 2400,
      "protein": 150,
      "workoutCompleted": false,
      "steps": 6200,
      "sleepHours": 6.8,
      "notes": "Выходной, тренировки не было"
    }
  }' | jq .
echo ""
echo ""

curl -s -X POST "$SERVER_URL" \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": 9,
    "method": "add_fitness_log",
    "params": {
      "date": "2026-04-03",
      "weight": 83.2,
      "calories": 2600,
      "protein": 170,
      "workoutCompleted": true,
      "steps": 9100,
      "sleepHours": 7.2,
      "notes": "Тренировка груди и трицепса"
    }
  }' | jq .
echo ""
echo ""

echo "8. Getting fitness summary for last 7 days..."
curl -s -X POST "$SERVER_URL" \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": 10,
    "method": "get_fitness_summary",
    "params": {
      "period": "last_7_days"
    }
  }' | jq .
echo ""
echo ""

echo "9. Scheduling a periodic fitness summary (every 2 minutes)..."
curl -s -X POST "$SERVER_URL" \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": 11,
    "method": "schedule_reminder",
    "params": {
      "delayMinutes": 2,
      "message": "fitness summary check"
    }
  }' | jq .
echo ""
echo ""

echo "10. Getting all tasks..."
curl -s -X POST "$SERVER_URL" \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": 12,
    "method": "get_pending_reminders"
  }' | jq .
echo ""
echo ""

echo "======================================"
echo "Test completed!"
echo "======================================"
echo ""
echo "Check the MCP server console for reminder execution logs"
echo "Scheduled reminders execute automatically every 10 seconds (check interval)"
