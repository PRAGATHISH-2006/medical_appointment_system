import sqlite3
import os

db_path = r'c:\medical_appointment_system\instance\medical_appointment.db'

def migrate():
    if not os.path.exists(db_path):
        print("Database not found. Re-creating all...")
        return

    conn = sqlite3.connect(db_path)
    cursor = conn.cursor()

    print("Migrating User table...")
    try:
        cursor.execute("ALTER TABLE user ADD COLUMN consultation_fee FLOAT DEFAULT 0.0")
        print("Added consultation_fee to user")
    except sqlite3.OperationalError as e:
        print(f"User table skipped or error: {e}")

    print("Migrating Appointment table...")
    try:
        cursor.execute("ALTER TABLE appointment ADD COLUMN payment_status TEXT DEFAULT 'pending'")
        cursor.execute("ALTER TABLE appointment ADD COLUMN payment_method TEXT")
        cursor.execute("ALTER TABLE appointment ADD COLUMN amount FLOAT")
        print("Added payment columns to appointment")
    except sqlite3.OperationalError as e:
        print(f"Appointment table skipped or error: {e}")

    print("Creating Payment table...")
    try:
        cursor.execute('''
            CREATE TABLE IF NOT EXISTS payment (
                id INTEGER PRIMARY KEY,
                appointment_id INTEGER NOT NULL,
                amount FLOAT NOT NULL,
                transaction_id VARCHAR(100),
                payment_method VARCHAR(50),
                status VARCHAR(20),
                created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                FOREIGN KEY(appointment_id) REFERENCES appointment(id)
            )
        ''')
        print("Created payment table")
    except sqlite3.OperationalError as e:
        print(f"Payment table error: {e}")

    conn.commit()
    conn.close()
    print("Migration complete!")

if __name__ == '__main__':
    migrate()
