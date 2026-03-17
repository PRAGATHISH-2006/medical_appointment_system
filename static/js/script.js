// Global application state
let appState = {
    currentModal: null,
    currentPrescriptionId: null
};

// Initialize when DOM is loaded
document.addEventListener('DOMContentLoaded', function () {
    initializeApp();
});

// Main initialization function
function initializeApp() {
    // Initialize date inputs
    initDateInputs();

    // Initialize modals
    initModals();

    // Initialize form validations
    initFormValidations();

    // Set current year in footer
    setCurrentYear();
}

// Initialize date inputs
function initDateInputs() {
    const dateInputs = document.querySelectorAll('input[type="date"]');
    dateInputs.forEach(input => {
        if (!input.value) {
            const today = new Date().toISOString().split('T')[0];
            input.value = today;
            input.min = today;
        }
    });
}

// Initialize modals
function initModals() {
    // Close modal when clicking outside
    document.addEventListener('click', function (event) {
        if (event.target.classList.contains('modal')) {
            closeModal(event.target.id);
        }
    });

    // Close modal with Escape key
    document.addEventListener('keydown', function (event) {
        if (event.key === 'Escape' && appState.currentModal) {
            closeModal(appState.currentModal);
        }
    });
}

// Initialize form validations
function initFormValidations() {
    const forms = document.querySelectorAll('form');
    forms.forEach(form => {
        form.addEventListener('submit', function (event) {
            if (!validateForm(this)) {
                event.preventDefault();
            }
        });
    });
}

// Set current year in footer
function setCurrentYear() {
    const yearElements = document.querySelectorAll('.current-year');
    const currentYear = new Date().getFullYear();
    yearElements.forEach(el => {
        el.textContent = currentYear;
    });
}

// ===== MODAL FUNCTIONS =====

function openModal(modalId) {
    const modal = document.getElementById(modalId);
    if (modal) {
        modal.style.display = 'flex';
        appState.currentModal = modalId;
        document.body.style.overflow = 'hidden';

        // Focus first input in modal
        const firstInput = modal.querySelector('input, select, textarea, button');
        if (firstInput) firstInput.focus();
    }
}

function closeModal(modalId) {
    const modal = document.getElementById(modalId);
    if (modal) {
        modal.style.display = 'none';
        appState.currentModal = null;
        document.body.style.overflow = 'auto';
    }
}

// ===== FORM VALIDATION =====

function validateForm(form) {
    const requiredInputs = form.querySelectorAll('[required]');
    let isValid = true;

    for (let input of requiredInputs) {
        if (!input.value.trim()) {
            showAlert(`Please fill in ${input.name || 'this field'}`, 'error');
            input.focus();
            isValid = false;
            break;
        }

        // Email validation
        if (input.type === 'email' && !isValidEmail(input.value)) {
            showAlert('Please enter a valid email address', 'error');
            input.focus();
            isValid = false;
            break;
        }

        // Password validation for register
        if (input.type === 'password' && input.name === 'password' && input.value.length < 6) {
            showAlert('Password must be at least 6 characters long', 'error');
            input.focus();
            isValid = false;
            break;
        }
    }

    // Confirm password validation
    const password = form.querySelector('input[name="password"]');
    const confirmPassword = form.querySelector('input[name="confirm_password"]');
    if (password && confirmPassword && password.value !== confirmPassword.value) {
        showAlert('Passwords do not match!', 'error');
        confirmPassword.focus();
        isValid = false;
    }

    return isValid;
}

function isValidEmail(email) {
    const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    return emailRegex.test(email);
}

// ===== ALERT FUNCTIONS =====

function showAlert(message, type = 'info') {
    // Remove existing alerts
    const existingAlerts = document.querySelectorAll('.custom-alert');
    existingAlerts.forEach(alert => alert.remove());

    // Create alert element
    const alertDiv = document.createElement('div');
    alertDiv.className = `custom-alert alert-${type}`;
    alertDiv.innerHTML = `
        <span>${message}</span>
        <button onclick="this.parentElement.remove()">&times;</button>
    `;

    // Add styles
    alertDiv.style.cssText = `
        position: fixed;
        top: 20px;
        right: 20px;
        padding: 1rem 1.5rem;
        border-radius: 5px;
        color: white;
        display: flex;
        justify-content: space-between;
        align-items: center;
        min-width: 300px;
        max-width: 400px;
        z-index: 9999;
        animation: slideIn 0.3s ease;
    `;

    // Set background color based on type
    const colors = {
        success: '#2ecc71',
        error: '#e74c3c',
        warning: '#f39c12',
        info: '#3498db'
    };
    alertDiv.style.backgroundColor = colors[type] || colors.info;

    // Add close button styles
    const closeBtn = alertDiv.querySelector('button');
    closeBtn.style.cssText = `
        background: none;
        border: none;
        color: white;
        font-size: 1.5rem;
        cursor: pointer;
        margin-left: 1rem;
    `;

    // Add animation
    const style = document.createElement('style');
    style.textContent = `
        @keyframes slideIn {
            from { transform: translateX(100%); opacity: 0; }
            to { transform: translateX(0); opacity: 1; }
        }
    `;
    document.head.appendChild(style);

    // Add to document
    document.body.appendChild(alertDiv);

    // Auto remove after 5 seconds
    setTimeout(() => {
        if (alertDiv.parentElement) {
            alertDiv.remove();
        }
    }, 5000);
}

// ===== API FUNCTIONS =====

async function approveDoctor(doctorId) {
    if (!confirm('Are you sure you want to approve this doctor?')) return;

    try {
        const response = await fetch(`/api/approve-doctor/${doctorId}`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            }
        });

        const data = await response.json();

        if (data.success) {
            showAlert(data.message, 'success');
            setTimeout(() => location.reload(), 1500);
        } else {
            showAlert(data.error || 'Error approving doctor', 'error');
        }
    } catch (error) {
        showAlert('Network error occurred', 'error');
    }
}

async function rejectDoctor(doctorId) {
    if (!confirm('Are you sure you want to reject this doctor?')) return;

    try {
        const response = await fetch(`/api/reject-doctor/${doctorId}`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            }
        });

        const data = await response.json();

        if (data.success) {
            showAlert(data.message, 'success');
            setTimeout(() => location.reload(), 1500);
        } else {
            showAlert(data.error || 'Error rejecting doctor', 'error');
        }
    } catch (error) {
        showAlert('Network error occurred', 'error');
    }
}

async function approveAppointment(appointmentId) {
    if (!confirm('Are you sure you want to approve this appointment?')) return;

    try {
        const response = await fetch(`/api/approve-appointment/${appointmentId}`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            }
        });

        const data = await response.json();

        if (data.success) {
            showAlert(data.message, 'success');
            setTimeout(() => location.reload(), 1500);
        } else {
            showAlert(data.error || 'Error approving appointment', 'error');
        }
    } catch (error) {
        showAlert('Network error occurred', 'error');
    }
}

async function rejectAppointment(appointmentId) {
    if (!confirm('Are you sure you want to reject this appointment?')) return;

    try {
        const response = await fetch(`/api/reject-appointment/${appointmentId}`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            }
        });

        const data = await response.json();

        if (data.success) {
            showAlert(data.message, 'success');
            setTimeout(() => location.reload(), 1500);
        } else {
            showAlert(data.error || 'Error rejecting appointment', 'error');
        }
    } catch (error) {
        showAlert('Network error occurred', 'error');
    }
}

async function deleteDoctor(doctorId, doctorName) {
    if (!confirm(`Are you sure you want to delete Dr. ${doctorName}? This action cannot be undone.`)) return;

    try {
        const response = await fetch(`/api/delete-doctor/${doctorId}`, {
            method: 'DELETE',
            headers: {
                'Content-Type': 'application/json'
            }
        });

        const data = await response.json();

        if (data.success) {
            showAlert(data.message, 'success');
            setTimeout(() => location.reload(), 1500);
        } else {
            showAlert(data.error || 'Error deleting doctor', 'error');
        }
    } catch (error) {
        showAlert('Network error occurred', 'error');
    }
}

async function markNotificationAsRead(notifId) {
    try {
        const response = await fetch(`/api/mark-notification-read/${notifId}`, {
            method: 'POST'
        });
        const data = await response.json();
        if (data.success) {
            const el = document.getElementById(`notif-${notifId}`);
            if (el) {
                el.classList.remove('unread');
                el.classList.add('read');
                const btn = el.querySelector('.mark-read-btn');
                if (btn) btn.remove();
            }

            // Update navbar badge count
            const badge = document.querySelector('.nav-icon .badge');
            if (badge) {
                let count = parseInt(badge.textContent);
                if (count > 1) {
                    badge.textContent = count - 1;
                } else {
                    badge.remove();
                }
            }

            showAlert('Notification marked as read', 'success');
        }
    } catch (error) {
        console.error('Error marking as read');
        showAlert('Error marking notification as read', 'error');
    }
}

async function removeCurrentDoctor(currentDoctorId) {
    if (!confirm('Are you sure you want to remove this doctor from your current doctors?')) return;

    try {
        const response = await fetch(`/api/remove-current-doctor/${currentDoctorId}`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            }
        });

        const data = await response.json();

        if (data.success) {
            showAlert(data.message, 'success');
            setTimeout(() => location.reload(), 1500);
        } else {
            showAlert(data.error || 'Error removing doctor', 'error');
        }
    } catch (error) {
        showAlert('Network error occurred', 'error');
    }
}

async function viewPrescription(prescriptionId) {
    try {
        const response = await fetch(`/api/get-prescription/${prescriptionId}`);
        const data = await response.json();

        if (data.success) {
            const prescription = data.prescription;
            openPrescriptionModal(prescription);
        } else {
            showAlert(data.error || 'Error loading prescription', 'error');
        }
    } catch (error) {
        showAlert('Network error occurred', 'error');
    }
}

function openPrescriptionModal(prescription) {
    const modalContent = `
        <div class="modal-header">
            <h3><i class="fas fa-prescription"></i> Prescription Details</h3>
            <button class="close" onclick="closeModal('prescriptionModal')">&times;</button>
        </div>
        <div class="modal-body">
            <div class="prescription-container">
                <div class="prescription-header">
                    <h3>Medical Prescription</h3>
                    <p><i class="fas fa-calendar-alt"></i> Date: ${prescription.created_at}</p>
                </div>
                <div class="prescription-content">
                    <div class="prescription-section">
                        <h4><i class="fas fa-user"></i> Patient Information</h4>
                        <p><strong>Name:</strong> ${prescription.patient_name}</p>
                    </div>
                    <div class="prescription-section">
                        <h4><i class="fas fa-user-md"></i> Doctor Information</h4>
                        <p><strong>Name:</strong> Dr. ${prescription.doctor_name}</p>
                    </div>
                    <div class="prescription-section">
                        <h4><i class="fas fa-pills"></i> Medicines</h4>
                        <p>${prescription.medicines.replace(/\n/g, '<br>')}</p>
                    </div>
                    <div class="prescription-section">
                        <h4><i class="fas fa-clock"></i> Dosage</h4>
                        <p>${prescription.dosage.replace(/\n/g, '<br>')}</p>
                    </div>
                    <div class="prescription-section">
                        <h4><i class="fas fa-info-circle"></i> Instructions</h4>
                        <p>${prescription.instructions.replace(/\n/g, '<br>') || 'No specific instructions'}</p>
                    </div>
                    ${prescription.notes ? `
                    <div class="prescription-section">
                        <h4><i class="fas fa-sticky-note"></i> Additional Notes</h4>
                        <p>${prescription.notes.replace(/\n/g, '<br>')}</p>
                    </div>
                    ` : ''}
                </div>
                <div class="prescription-footer">
                    <div class="doctor-signature">
                        <p>Dr. ${prescription.doctor_name}</p>
                        <div class="signature-line">Authorized Signature</div>
                    </div>
                    <div class="prescription-id">
                        <small>Ref: MED-${prescription.id.toString().padStart(6, '0')}</small>
                    </div>
                </div>
            </div>
        </div>
    `;

    // Create or update modal
    let modal = document.getElementById('prescriptionModal');
    if (!modal) {
        modal = document.createElement('div');
        modal.id = 'prescriptionModal';
        modal.className = 'modal';
        modal.style.display = 'none';
        document.body.appendChild(modal);
    }

    modal.innerHTML = `
        <div class="modal-content" style="max-width: 600px;">
            ${modalContent}
        </div>
    `;

    openModal('prescriptionModal');
}

// ===== UTILITY FUNCTIONS =====

function toggleDoctorFields() {
    const role = document.getElementById('role').value;
    const doctorFields = document.getElementById('doctor-fields');
    if (doctorFields) {
        doctorFields.style.display = role === 'doctor' ? 'block' : 'none';
    }
}

function printPrescription() {
    window.print();
}

function downloadPrescription(prescriptionId) {
    window.location.href = `/patient/prescription/download/${prescriptionId}`;
}

// ===== EVENT LISTENERS FOR DYNAMIC CONTENT =====

document.addEventListener('click', function (event) {
    // Handle approve doctor buttons
    const approveDoctorBtn = event.target.closest('.approve-doctor-btn');
    if (approveDoctorBtn) {
        approveDoctor(approveDoctorBtn.dataset.doctorId);
    }

    // Handle reject doctor buttons
    const rejectDoctorBtn = event.target.closest('.reject-doctor-btn');
    if (rejectDoctorBtn) {
        rejectDoctor(rejectDoctorBtn.dataset.doctorId);
    }

    // Handle approve appointment buttons
    const approveAppBtn = event.target.closest('.approve-appointment-btn');
    if (approveAppBtn) {
        approveAppointment(approveAppBtn.dataset.appointmentId);
    }

    // Handle reject appointment buttons
    const rejectAppBtn = event.target.closest('.reject-appointment-btn');
    if (rejectAppBtn) {
        rejectAppointment(rejectAppBtn.dataset.appointmentId);
    }

    // Handle delete doctor buttons
    const deleteDoctorBtn = event.target.closest('.delete-doctor-btn');
    if (deleteDoctorBtn) {
        deleteDoctor(deleteDoctorBtn.dataset.doctorId, deleteDoctorBtn.dataset.doctorName);
    }

    // Handle remove current doctor buttons
    const removeDoctorBtn = event.target.closest('.remove-doctor-btn');
    if (removeDoctorBtn) {
        removeCurrentDoctor(removeDoctorBtn.dataset.currentDoctorId);
    }

    // Handle view prescription buttons
    const viewPrescriptionBtn = event.target.closest('.view-prescription-btn');
    if (viewPrescriptionBtn) {
        viewPrescription(viewPrescriptionBtn.dataset.prescriptionId);
    }

    // Handle mark notification read buttons
    const markReadBtn = event.target.closest('.mark-read-btn');
    if (markReadBtn) {
        markNotificationAsRead(markReadBtn.dataset.notifId);
    }

    // Handle send notification button (Admin)
    const sendNotifBtn = event.target.closest('.send-notif-btn');
    if (sendNotifBtn) {
        const userId = sendNotifBtn.dataset.userId;
        const userName = sendNotifBtn.dataset.userName;

        document.getElementById('notif-user-id').value = userId;
        document.getElementById('notif-user-name').textContent = userName;
        document.getElementById('notif-message').value = '';

        openModal('notificationModal');
    }
});

// Notification Form Submission
document.addEventListener('submit', async function (event) {
    if (event.target.id === 'notificationForm') {
        event.preventDefault();

        const userId = document.getElementById('notif-user-id').value;
        const type = document.getElementById('notif-type').value;
        const message = document.getElementById('notif-message').value;

        if (!message.trim()) {
            showAlert('Please enter a message', 'error');
            return;
        }

        try {
            const response = await fetch('/api/send-notification', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify({
                    user_id: userId,
                    type: type,
                    message: message
                })
            });

            const data = await response.json();

            if (data.success) {
                showAlert(data.message, 'success');
                closeModal('notificationModal');
            } else {
                showAlert(data.error || 'Error sending notification', 'error');
            }
        } catch (error) {
            showAlert('Network error occurred', 'error');
        }
    }
});