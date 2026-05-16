<div align="center">

# 🏥 Medical Appointment & Health Management System

<img src="https://img.shields.io/badge/Python-3776AB?style=for-the-badge&logo=python&logoColor=white" />
<img src="https://img.shields.io/badge/Flask-000000?style=for-the-badge&logo=flask&logoColor=white" />
<img src="https://img.shields.io/badge/SQLite-07405E?style=for-the-badge&logo=sqlite&logoColor=white" />
<img src="https://img.shields.io/badge/Bootstrap-563D7C?style=for-the-badge&logo=bootstrap&logoColor=white" />

A clean, fast, and modern server-side rendered application for booking and managing medical appointments.

</div>

---

## 🌟 Overview

The **Medical Appointment System** is a robust web application built entirely with **Python and Flask**. It allows patients to easily browse available doctors, view medical services, and securely book appointments. It also features a dedicated Admin Dashboard for managing the platform's doctors and viewing all scheduled appointments.

This project was recently rewritten from the ground up to provide a lightning-fast, monolithic architecture using server-side rendered **Jinja2** templates styled with beautiful **Bootstrap 5** components.

## 🚀 Key Features

- **👨‍⚕️ Doctor Directory:** View a comprehensive list of doctors, their specializations, experience, and consultation fees.
- **📅 Easy Booking:** Patients can seamlessly select a preferred date and time to book an appointment with their chosen doctor.
- **🔐 Secure Authentication:** Full user authentication system built with `Flask-Login` (Register, Login, Logout).
- **🛡️ Admin Dashboard:** Protected routes allowing administrators to oversee all platform activity, including viewing all booked appointments and registered doctors.
- **📱 Responsive UI:** A fully responsive, mobile-friendly design powered by Bootstrap 5.

---

## 🛠️ Technology Stack

| Component | Technology | Description |
| :--- | :--- | :--- |
| **Backend Framework** | `Flask` | Lightweight and powerful Python web framework. |
| **Database** | `SQLite` | Relational database to store users, doctors, and appointments. |
| **ORM** | `SQLAlchemy` | Object-Relational Mapping for secure database queries. |
| **Frontend Styling** | `Bootstrap 5` | Modern CSS framework for responsive layout and UI components. |
| **Templating** | `Jinja2` | Dynamic server-side HTML rendering. |

---

## ⚙️ How to Run Locally

Follow these instructions to set up the project on your local machine.

### 1. Clone the Repository
```bash
git clone https://github.com/PRAGATHISH-2006/medical_appointment_system.git
cd "medical_appointment_system"
```

### 2. Create a Virtual Environment
It is recommended to use a virtual environment to manage dependencies.
```bash
# Windows
python -m venv venv
.\venv\Scripts\activate

# macOS / Linux
python3 -m venv venv
source venv/bin/activate
```

### 3. Install Dependencies
```bash
pip install Flask Flask-SQLAlchemy Flask-Login Flask-WTF Werkzeug
```

### 4. Run the Application
Start the Flask development server. The SQLite database will be created automatically on the first run!
```bash
python app.py
```

### 5. Access the Platform
Open your web browser and navigate to:
> **http://127.0.0.1:5000**

---

## 🔑 Default Administrator Account

Upon the first run, the system automatically generates a default administrator account. You can use this to access the **Admin Dashboard**:

- **Email:** `admin@medicare.com`
- **Password:** `admin123`

---

<div align="center">
  <i>Developed with ❤️ using Python and Flask.</i>
</div>
