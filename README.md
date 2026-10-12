# Expense Tracker

## Project Overview
Expense Tracker is a web application designed to help users manage their income and expenses. Users can record transactions, view transaction history, update or delete records, and monitor their financial balance.

## Technologies Used
- **Frontend:** HTML, CSS, JavaScript
- **Backend:** Java
- **API:** Java HTTP Server and REST-style API endpoints
- **Database:** PostgreSQL
- **Database Connectivity:** JDBC
- **Build Tool:** Maven
- **Development Tools:** Visual Studio Code and IntelliJ IDEA

## Features
- Add income and expense transactions
- View saved transactions
- Update transaction details
- Delete transactions
- Filter transactions by type and category
- Calculate total income, total expenses, and balance
- Store transaction data in PostgreSQL

## Project Structure
- `frontend/` — HTML, CSS, and JavaScript files
- `backend/` — Java source code and Maven configuration
- `database.sql` — SQL script for creating the transactions table

## Prerequisites
Install Java 17 or later, Maven, PostgreSQL, Visual Studio Code, and IntelliJ IDEA.

## Setup Instructions

### 1. Create the database
Create a PostgreSQL database named `expense_tracker` and run the SQL statements in `database.sql`.

### 2. Configure the database connection
Set the `DB_PASSWORD` environment variable to your PostgreSQL password. Check that the database name, username, and port match your local PostgreSQL configuration.

### 3. Start the backend
Open the `backend` folder in IntelliJ IDEA. Resolve Maven dependencies and run `TransactionServer.java`.

The API should be available at:
`http://localhost:8080/api/transactions`

### 4. Start the frontend
Open the `frontend` folder in Visual Studio Code and launch `index.html` using Live Server, or another local web server.

### 5. Test the application
Add an income or expense transaction and verify that it appears in the transaction list and is saved in PostgreSQL.

## Learning Objectives
This project demonstrates frontend development, Java backend programming, REST API communication, JDBC database connectivity, SQL table design, and CRUD operations.

## Author
Reshmi Menon
