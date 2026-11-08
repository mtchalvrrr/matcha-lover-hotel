(function () {
  'use strict';
  const form = document.getElementById('customerRegistrationForm');
  if (!form) return;
  const panels = Array.from(form.querySelectorAll('.registration-panel'));
  const indicators = Array.from(document.querySelectorAll('[data-step-indicator]'));
  let step = 0;

  window.registrationShowStep = function (nextStep) {
    step = Math.max(0, Math.min(nextStep, panels.length - 1));
    panels.forEach((panel, index) => {
      panel.hidden = index !== step;
      panel.classList.toggle('is-visible', index === step);
    });
    indicators.forEach((item, index) => {
      item.classList.toggle('is-active', index === step);
      item.classList.toggle('is-complete', index < step);
    });
    const fill = document.getElementById('registrationProgressFill');
    if (fill) fill.style.width = (step / (panels.length - 1) * 100) + '%';
    document.getElementById('registrationBack').hidden = step === 0;
    document.getElementById('registrationNext').hidden = step === panels.length - 1;
    document.getElementById('registrationSubmit').hidden = step !== panels.length - 1;
    document.getElementById('registrationError').hidden = true;
  };

  window.registrationNextStep = function () {
    const error = document.getElementById('registrationError');
    const fields = Array.from(panels[step].querySelectorAll('input[required]'));
    for (const field of fields) {
      if (!field.checkValidity()) {
        field.reportValidity();
        field.focus();
        return false;
      }
    }
    if (step === 0) {
      for (const id of ['firstName', 'lastName']) {
        const field = document.getElementById(id);
        if (!/^[\p{L}\p{M}][\p{L}\p{M} .’'-]*$/u.test(field.value.trim())) {
          error.textContent = 'Enter a valid name using letters only. Numbers are not allowed.';
          error.hidden = false;
          field.focus();
          return false;
        }
      }
    }
    if (step === 1) {
      const password = document.getElementById('password');
      const confirm = document.getElementById('confirmPassword');
      if (!(password.value.length >= 12 && /[A-Z]/.test(password.value) &&
          /[a-z]/.test(password.value) && /\d/.test(password.value) &&
          /[^A-Za-z0-9]/.test(password.value))) {
        error.textContent = 'Use at least 12 characters, including uppercase, lowercase, a number, and a special character.';
        error.hidden = false;
        password.focus();
        return false;
      }
      if (password.value !== confirm.value) {
        error.textContent = 'Your passwords do not match.';
        error.hidden = false;
        confirm.focus();
        return false;
      }
    }
    window.registrationShowStep(step + 1);
    return false;
  };

  window.registrationPreviousStep = function () {
    window.registrationShowStep(step - 1);
    return false;
  };

  form.addEventListener('submit', function (event) {
    if (step !== panels.length - 1) {
      event.preventDefault();
      return;
    }
    const password = document.getElementById('password');
    const confirm = document.getElementById('confirmPassword');
    if (password.value !== confirm.value) {
      event.preventDefault();
      document.getElementById('registrationError').textContent = 'Your passwords do not match.';
      document.getElementById('registrationError').hidden = false;
    }
  });

  document.querySelectorAll('[data-toggle-password]').forEach(button => {
    button.addEventListener('click', function () {
      const input = document.getElementById(button.dataset.togglePassword);
      const reveal = input.type === 'password';
      input.type = reveal ? 'text' : 'password';
      button.textContent = reveal ? 'Hide' : 'Show';
    });
  });

  const normalizeName = input => {
    input.value = input.value.replace(/[^\p{L}\p{M} .’'-]/gu, '')
      .replace(/(^|[\s'-])([\p{L}])/gu, (_, prefix, letter) => prefix + letter.toLocaleUpperCase());
  };
  ['firstName', 'lastName'].forEach(id => {
    const field = document.getElementById(id);
    field.addEventListener('input', () => normalizeName(field));
    field.addEventListener('blur', () => normalizeName(field));
  });

  const password = document.getElementById('password');
  password.addEventListener('input', function () {
    const checks = [password.value.length >= 12, /[A-Z]/.test(password.value),
      /[a-z]/.test(password.value), /\d/.test(password.value), /[^A-Za-z0-9]/.test(password.value)];
    const score = checks.filter(Boolean).length;
    const fill = document.getElementById('passwordStrengthFill');
    fill.style.width = (score * 20) + '%';
    fill.className = score === 5 ? 'is-strong' : score >= 3 ? 'is-medium' : '';
    document.getElementById('passwordStrengthText').textContent =
      score === 5 ? 'Strong password' : 'Password requirements met: ' + score + ' of 5';
  });
  const confirm = document.getElementById('confirmPassword');
  confirm.addEventListener('input', function () {
    const match = document.getElementById('passwordMatchMessage');
    match.textContent = !confirm.value ? '' : confirm.value === password.value ? 'Passwords match' : 'Passwords do not match';
    match.classList.toggle('is-valid', !!confirm.value && confirm.value === password.value);
    match.classList.toggle('is-invalid', !!confirm.value && confirm.value !== password.value);
  });
  document.getElementById('idDocument').addEventListener('change', function () {
    document.getElementById('idFileName').textContent = this.files[0] ? this.files[0].name : 'No file selected';
  });
  window.registrationShowStep(0);
})();
