document.addEventListener('DOMContentLoaded', () => {
  const form = document.getElementById('roomReservationForm');
  const button = document.getElementById('reservationSubmitButton');
  const status = document.getElementById('reservationSubmitStatus');
  if (!form || !button || !status) return;

  form.addEventListener('submit', (event) => {
    if (!form.reportValidity()) {
      event.preventDefault();
      return;
    }
    button.disabled = true;
    button.textContent = 'Opening secure checkout…';
    status.hidden = false;
    status.textContent = 'Your reservation is being prepared. You will be redirected to Xendit Test Mode.';
  });
});
