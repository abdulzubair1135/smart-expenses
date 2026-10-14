# SMART EXPENSES TRACKER - API CONTRACT: EXPENSE GROUPS & TRIPS

## 1. Authentication
All endpoints require a Bearer JWT token in the `Authorization` header:
```http
Authorization: Bearer <jwt_token>
```
Cross-user access is strictly forbidden; `userId` is extracted from verified token claims.

---

## 2. Trips Endpoints

### 2.1 List User Trips
`GET /api/v1/trips`
- **Query Parameters:** `status` (`active`, `completed`, `archived`)
- **Response (200 OK):**
```json
{
  "success": true,
  "data": [
    {
      "id": "trip_mumbai_001",
      "userId": "usr_demo_001",
      "name": "Mumbai Visit",
      "destination": "Mumbai, Maharashtra",
      "startDate": "2026-11-12",
      "endDate": "2026-11-15",
      "description": "Family trip and sightseeing",
      "status": "active",
      "createdAt": 1792000000000,
      "updatedAt": 1792000000000,
      "totalSpentMinor": 470000,
      "transactionCount": 4
    }
  ]
}
```

### 2.2 Create Trip
`POST /api/v1/trips`
- **Request Body:**
```json
{
  "name": "Goa Vacation",
  "destination": "Goa",
  "startDate": "2026-12-20",
  "endDate": "2026-12-25",
  "description": "Year end beach trip"
}
```
- **Response (201 Created):** Returns created `Trip` object.

### 2.3 Get Trip Details & Linked Transactions
`GET /api/v1/trips/:id`
`GET /api/v1/trips/:id/transactions`
- **Response (200 OK):** Returns trip object and array of linked travel transactions.

### 2.4 Update Trip
`PATCH /api/v1/trips/:id`
- **Request Body:** Partial trip fields (`name`, `destination`, `startDate`, `endDate`, `description`, `status`).
- **Response (200 OK):** Updated trip object.

### 2.5 Safe Delete / Archive Trip
`DELETE /api/v1/trips/:id`
- If transactions are linked: Transitions status to `archived` to prevent foreign key corruption.
- If zero transactions linked: Permanently deletes trip row.

---

## 3. Expense Summary Endpoints

### 3.1 Daily Expense Summary
`GET /api/v1/expenses/summary/daily?date=2026-10-15`
- **Response (200 OK):**
```json
{
  "success": true,
  "data": {
    "date": "2026-10-15",
    "totalExpenseMinor": 125000,
    "transactionCount": 3,
    "categories": [
      { "category": "Food", "amountMinor": 80000 },
      { "category": "Transport", "amountMinor": 45000 }
    ],
    "previousDayTotalMinor": 90000,
    "differenceMinor": 35000
  }
}
```

### 3.2 Monthly Expense Summary
`GET /api/v1/expenses/summary/monthly?month=2026-10`
- **Response (200 OK):**
```json
{
  "success": true,
  "data": {
    "month": "2026-10",
    "totalExpenseMinor": 3450000,
    "transactionCount": 24,
    "categories": [
      { "category": "Groceries", "amountMinor": 1500000 },
      { "category": "Bills", "amountMinor": 1200000 }
    ],
    "previousMonthTotalMinor": 3200000,
    "differenceMinor": 250000
  }
}
```

### 3.3 Travel Expenses Summary
`GET /api/v1/expenses/summary/travel`
- **Response (200 OK):**
```json
{
  "success": true,
  "data": {
    "totalTravelMinor": 470000,
    "travelTransactionCount": 4,
    "tripsCount": 1
  }
}
```

### 3.4 Special Expenses Summary
`GET /api/v1/expenses/summary/special`
- **Response (200 OK):**
```json
{
  "success": true,
  "data": {
    "totalSpecialMinor": 150000,
    "specialTransactionCount": 2,
    "importantCount": 1
  }
}
```
