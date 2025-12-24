<template>
    <div class="min-h-screen bg-gray-900 text-white p-8">
        <header class="mb-8 flex justify-between items-center">
            <div>
                <h1
                    class="text-4xl font-bold bg-clip-text text-transparent bg-gradient-to-r from-blue-400 to-purple-500">
                    KinesisFlow Mission Control
                </h1>
                <p class="text-gray-400 mt-2">Real-time Market Alerting System</p>
            </div>
            <div v-if="connected" class="flex items-center space-x-2">
                <span class="relative flex h-3 w-3">
                    <span
                        class="animate-ping absolute inline-flex h-full w-full rounded-full bg-green-400 opacity-75"></span>
                    <span class="relative inline-flex rounded-full h-3 w-3 bg-green-500"></span>
                </span>
                <span class="text-green-500 font-mono">SYSTEM ONLINE</span>
            </div>
            <div v-else class="text-red-500 font-mono">OFFLINE</div>
        </header>

        <!-- Error Banner -->
        <div v-if="errorMsg" class="bg-red-500/20 border border-red-500 text-red-100 p-4 mb-6 rounded">
            <strong>Connection Error:</strong> {{ errorMsg }}
        </div>

        <div class="grid grid-cols-1 md:grid-cols-4 gap-6">
            <!-- Stats Cards -->
            <div class="bg-gray-800 rounded-xl p-6 border border-gray-700">
                <h3 class="text-gray-500 text-sm font-uppercase tracking-wider">Active Alerts</h3>
                <p class="text-3xl font-mono mt-2 text-white">{{ alerts.length }}</p>
            </div>

            <div class="bg-gray-800 rounded-xl p-6 border border-gray-700">
                <h3 class="text-gray-500 text-sm font-uppercase tracking-wider">Events Processed</h3>
                <p class="text-3xl font-mono mt-2 text-blue-400">{{ eventCount }}</p>
            </div>

            <!-- Main Chart -->
            <div class="md:col-span-3 bg-gray-800 rounded-xl p-6 border border-gray-700 h-[400px]">
                <RealTimeChart :chart-data="chartData" :chart-options="chartOptions" />
            </div>

            <!-- Live Feed -->
            <div
                class="md:col-span-1 bg-gray-800 rounded-xl p-6 border border-gray-700 h-[400px] overflow-y-auto font-mono text-xs">
                <h3 class="text-gray-500 text-sm font-uppercase tracking-wider mb-4">Live Notifications</h3>
                <ul class="space-y-2">
                    <li v-for="(notif, index) in notifications" :key="index"
                        class="p-2 bg-gray-700/50 rounded border-l-2 border-yellow-500">
                        <span class="text-yellow-400 font-bold">{{ notif.asset || 'BTC' }}</span>
                        <span class="text-gray-300"> crossed </span>
                        <span class="text-white">{{ notif.price || '0.00' }}</span>
                        <div class="text-gray-500 text-[10px] mt-1">{{ new Date().toLocaleTimeString() }}</div>
                    </li>
                </ul>
            </div>
        </div>
    </div>
</template>

<script setup>
import { ref, onMounted, shallowRef } from 'vue'
import axios from 'axios'
import RealTimeChart from './components/RealTimeChart.vue'

const connected = ref(false)
const eventCount = ref(0)
const notifications = ref([])
const alerts = ref([])
const errorMsg = ref('')

// Chart Data - Use shallowRef to avoid deep reactivity conflicts with Chart.js
const chartData = shallowRef({
    labels: [],
    datasets: [
        {
            label: 'Throughput (Events/Sec)',
            backgroundColor: 'rgba(59, 130, 246, 0.2)',
            borderColor: '#3b82f6',
            pointBackgroundColor: '#3b82f6',
            data: [],
            tension: 0.4,
            fill: true
        }
    ]
})

const chartOptions = {
    responsive: true,
    maintainAspectRatio: false,
    scales: {
        y: {
            grid: { color: '#374151' },
            ticks: { color: '#9ca3af' },
            beginAtZero: false
        },
        x: {
            grid: { display: false },
            ticks: { color: '#9ca3af' }
        }
    },
    plugins: {
        legend: { display: true, labels: { color: '#fff' } }
    }
}

// Logic
const API_BASE = 'http://localhost:8081'
const API_URL = `${API_BASE}/auth`
const WS_URL = 'ws://localhost:8081/ws/notifications'

const registerAndLogin = async () => {
    const username = `demo-${Math.floor(Math.random() * 10000)}`
    const password = 'password'

    try {
        errorMsg.value = ''
        try {
            await axios.post(`${API_URL}/users`, { username, password })
        } catch (e) {
            // User might exist
        }

        // Login
        const response = await axios.post(`${API_URL}/login`, { username, password })
        const token = response.data.token

        // Create demo alerts for ALL simulated assets
        // Prices matched to MarketDataSimulator
        const assets = [
            { symbol: 'BTC', price: 45000.00 },
            { symbol: 'ETH', price: 3200.00 },
            { symbol: 'SOL', price: 110.00 },
            { symbol: 'ADA', price: 1.20 },
            { symbol: 'DOT', price: 15.00 }
        ]

        for (const asset of assets) {
            await createDemoAlert(token, asset.symbol, asset.price)
        }

        connectWebSocket(token)
    } catch (e) {
        console.error("Auth failed", e)
        errorMsg.value = `Login failed: ${e.message}. Is backend running on 8081?`
        scheduleReconnect() // Retry login if backend is down
    }
}

const createDemoAlert = async (token, asset, price) => {
    try {
        await axios.post(`${API_BASE}/api/alerts/subscribe`, {
            asset: asset,
            price: price,
            comparisonType: 1
        }, {
            headers: { Authorization: `Bearer ${token}` }
        })
        alerts.value.push({ asset: asset, condition: `> ${price}` })
    } catch (e) {
        console.error(`Failed to create alert for ${asset}`, e)
    }
}

// Store token for reconnection
let currentToken = null

const connectWebSocket = (token) => {
    if (token) currentToken = token
    if (connected.value) return

    const ws = new WebSocket(`${WS_URL}?token=${currentToken}`)

    ws.onopen = () => {
        connected.value = true
        errorMsg.value = ''
        console.log("WS Connected")
    }

    ws.onmessage = (event) => {
        try {
            const data = JSON.parse(event.data)
            notifications.value.unshift(data)
            if (notifications.value.length > 50) notifications.value.pop()
            eventCount.value++
        } catch (e) {
            console.error("Parse error", e)
        }
    }

    ws.onclose = (event) => {
        connected.value = false
        console.log("WS Closed", event.code)
        if (event.code !== 1000) {
            errorMsg.value = `Connection lost. Auto-reconnecting...`
            scheduleReconnect()
        }
    }
}

let reconnectTimer = null
const scheduleReconnect = () => {
    if (reconnectTimer) clearTimeout(reconnectTimer)
    reconnectTimer = setTimeout(() => {
        console.log("Attempting reconnect...")
        if (currentToken) {
            connectWebSocket(currentToken)
        } else {
            registerAndLogin() // Full re-auth if no token
        }
    }, 3000)
}

// Simulation for the chart
let lastEventCount = 0

setInterval(() => {
    const now = new Date().toLocaleTimeString()
    const currentLabels = chartData.value.labels || []
    const currentData = chartData.value.datasets[0].data || []

    const newLabels = [...currentLabels, now]

    // Calculate REAL throughput (events per second)
    // If disconnected, force 0
    let val = 0
    if (connected.value) {
        const currentTotal = eventCount.value
        val = currentTotal - lastEventCount
        lastEventCount = currentTotal
    }

    const newData = [...currentData, val]

    if (newLabels.length > 20) {
        newLabels.shift()
        newData.shift()
    }

    chartData.value = {
        labels: newLabels,
        datasets: [
            {
                ...chartData.value.datasets[0],
                data: newData
            }
        ]
    }
}, 1000)

onMounted(() => {
    registerAndLogin()
})

</script>
