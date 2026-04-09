#!/bin/bash

echo "======================================"
echo "Fitness MCP Server - Test Script"
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

echo "3. Adding fitness logs for the last 7 days..."
curl -s -X POST "$SERVER_URL" \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": 3,
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
    "id": 4,
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
    "id": 5,
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

curl -s -X POST "$SERVER_URL" \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": 6,
    "method": "add_fitness_log",
    "params": {
      "date": "2026-04-04",
      "weight": 83.0,
      "calories": 2450,
      "protein": 155,
      "workoutCompleted": true,
      "steps": 7800,
      "sleepHours": 7.0,
      "notes": "Тренировка спины"
    }
  }' | jq .
echo ""
echo ""

curl -s -X POST "$SERVER_URL" \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": 7,
    "method": "add_fitness_log",
    "params": {
      "date": "2026-04-05",
      "weight": 82.8,
      "calories": 2300,
      "protein": 140,
      "workoutCompleted": false,
      "steps": 5500,
      "sleepHours": 6.5,
      "notes": "Усталость, пропустил тренировку"
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
      "date": "2026-04-06",
      "weight": 82.7,
      "calories": 2550,
      "protein": 165,
      "workoutCompleted": true,
      "steps": 8900,
      "sleepHours": 7.3,
      "notes": "Тренировка плеч и бицепса"
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
      "date": "2026-04-07",
      "weight": 82.5,
      "calories": 2500,
      "protein": 160,
      "workoutCompleted": true,
      "steps": 8400,
      "sleepHours": 7.2,
      "notes": "Тренировка груди и трицепса, самочувствие норм"
    }
  }' | jq .
echo ""
echo ""

echo "4. Getting fitness summary for last 7 days..."
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

echo "5. Manually running scheduled summary..."
curl -s -X POST "$SERVER_URL" \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": 11,
    "method": "run_scheduled_summary"
  }' | jq .
echo ""
echo ""

echo "6. Getting latest scheduled summary..."
sleep 2
curl -s -X POST "$SERVER_URL" \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": 12,
    "method": "get_latest_scheduled_summary"
  }' | jq .
echo ""
echo ""

echo "======================================"
echo "Test completed!"
echo "======================================"
echo ""
echo "Note: Scheduler runs automatically every 1 minute (Demo mode)"
echo "Check the console output for automatic summary generation"