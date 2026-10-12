
const API_URL = "http://localhost:8080/api/transactions";

let transactions = [];
let editId = null;

const form = document.getElementById("transaction-form");
const typeInput = document.getElementById("type");
const amountInput = document.getElementById("amount");
const categoryInput = document.getElementById("category");
const dateInput = document.getElementById("date");
const descriptionInput = document.getElementById("description");
const submitBtn = document.getElementById("submit-btn");

const list = document.getElementById("transaction-list");
const incomeDisplay = document.getElementById("total-income");
const expenseDisplay = document.getElementById("total-expenses");
const balanceDisplay = document.getElementById("balance");

const filterType = document.getElementById("filter-type");
const filterCategory = document.getElementById("filter-category");

// HTML date: YYYY-MM-DD
// Java API date: DD/MM/YYYY
function toBackendDate(date) {
    if (!date) return "";

    const [year, month, day] = date.split("-");
    return `${day}/${month}/${year}`;
}

function toInputDate(date) {
    if (!date) return "";

    const [day, month, year] = date.split("/");
    return `${year}-${month}-${day}`;
}

function displayDate(date) {
    return date && date.includes("-")
        ? toBackendDate(date)
        : date || "";
}

async function apiRequest(url, options = {}) {
    const response = await fetch(url, options);

    if (response.status === 204) {
        if (!response.ok) {
            throw new Error("Request failed");
        }
        return null;
    }

    const result = await response.json();

    if (!response.ok) {
        throw new Error(result.error || "Request failed");
    }

    return result;
}

// Read all transactions from PostgreSQL through Java
async function loadTransactions() {
    list.textContent = "Loading transactions...";

    try {
        transactions = await apiRequest(API_URL);
        updateUI();
    } catch (error) {
        console.error("Load error:", error);
        list.textContent =
            "Unable to load data. Check your Java backend and database.";
    }
}

function updateUI() {
    list.replaceChildren();

    // Totals always represent all transactions,
    // regardless of the selected filters.
    let totalIncome = 0;
    let totalExpenses = 0;

    transactions.forEach(transaction => {
        const amount = Number(transaction.amount);

        if (transaction.type === "income") {
            totalIncome += amount;
        } else if (transaction.type === "expense") {
            totalExpenses += amount;
        }
    });

    incomeDisplay.textContent = `₹${totalIncome.toFixed(2)}`;
    expenseDisplay.textContent = `₹${totalExpenses.toFixed(2)}`;

    balanceDisplay.textContent =
        `₹${(totalIncome - totalExpenses).toFixed(2)}`;

    const filtered = transactions.filter(transaction => {
        const typeMatches =
            filterType.value === "all" ||
            transaction.type === filterType.value;

        const categoryMatches =
            filterCategory.value === "all" ||
            transaction.category === filterCategory.value;

        return typeMatches && categoryMatches;
    });

    if (filtered.length === 0) {
        list.textContent = "No transactions found.";
        return;
    }

    filtered.forEach(transaction => {
        const li = document.createElement("li");
        li.className = "transaction";

        const details = document.createElement("div");

        const title = document.createElement("strong");
        title.textContent = transaction.category;

        const description = document.createElement("span");
        description.textContent = transaction.description
            ? `: ${transaction.description}`
            : "";

        const date = document.createElement("small");
        date.textContent = displayDate(transaction.date);

        details.append(title, description,
            document.createElement("br"), date);

        const actions = document.createElement("div");
        actions.className = "transaction-actions";

        const amount = document.createElement("span");
        const sign = transaction.type === "income" ? "+" : "-";
        amount.textContent =
            `${sign}₹${Number(transaction.amount).toFixed(2)}`;

        const editButton = document.createElement("button");
        editButton.type = "button";
        editButton.className = "edit-button";
        editButton.textContent = "Edit";
        editButton.addEventListener("click", () => {
            editTransaction(transaction.id);
        });

        const deleteButton = document.createElement("button");
        deleteButton.type = "button";
        deleteButton.className = "delete-button";
        deleteButton.textContent = "Delete";
        deleteButton.addEventListener("click", () => {
            deleteTransaction(transaction.id);
        });

        actions.append(amount, editButton, deleteButton);
        li.append(details, actions);
        list.appendChild(li);
    });
}

// Add or update a transaction
form.addEventListener("submit", async event => {
    event.preventDefault();

    const amount = Number(amountInput.value);

    if (!Number.isFinite(amount) || amount <= 0) {
        alert("Enter an amount greater than zero.");
        return;
    }

    const transaction = {
        type: typeInput.value,
        amount,
        category: categoryInput.value,
        date: toBackendDate(dateInput.value),
        description: descriptionInput.value.trim()
    };

    const isEditing = editId !== null;
    const url = isEditing
        ? `${API_URL}/${editId}`
        : API_URL;

    submitBtn.disabled = true;

    try {
        await apiRequest(url, {
            method: isEditing ? "PUT" : "POST",
            headers: {
                "Content-Type": "application/json"
            },
            body: JSON.stringify(transaction)
        });

        alert(isEditing
            ? "Transaction updated successfully!"
            : "Transaction added successfully!");

        editId = null;
        submitBtn.textContent = "Add Transaction";
        form.reset();

        await loadTransactions();

    } catch (error) {
        console.error("Save error:", error);
        alert(error.message);
    } finally {
        submitBtn.disabled = false;
    }
});

// Edit a transaction
function editTransaction(id) {
    const transaction = transactions.find(
        item => String(item.id) === String(id)
    );

    if (!transaction) {
        alert("Transaction not found.");
        return;
    }

    typeInput.value = transaction.type;
    amountInput.value = transaction.amount;
    categoryInput.value = transaction.category;
    dateInput.value = toInputDate(transaction.date);
    descriptionInput.value = transaction.description || "";

    editId = transaction.id;
    submitBtn.textContent = "Update Transaction";

    form.scrollIntoView({
        behavior: "smooth",
        block: "start"
    });
}

// Delete a transaction
async function deleteTransaction(id) {
    if (!confirm("Are you sure you want to delete this transaction?")) {
        return;
    }

    try {
        await apiRequest(`${API_URL}/${id}`, {
            method: "DELETE"
        });

        if (String(editId) === String(id)) {
            editId = null;
            form.reset();
            submitBtn.textContent = "Add Transaction";
        }

        await loadTransactions();

    } catch (error) {
        console.error("Delete error:", error);
        alert(error.message);
    }
}

filterType.addEventListener("change", updateUI);
filterCategory.addEventListener("change", updateUI);

// Load saved records when the frontend opens
loadTransactions();
