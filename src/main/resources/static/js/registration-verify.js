(() => {
    const form = document.querySelector('form[action$="/register/verify"]');
    if (!form) return;

    const boxes = Array.from(form.querySelectorAll('.otp-digit'));
    const hidden = form.querySelector('#otp');
    if (!hidden || boxes.length === 0) return;

    const sync = () => {
        hidden.value = boxes.map(box => box.value).join('');
        boxes.forEach(box => box.setAttribute('aria-invalid', 'false'));
    };

    boxes.forEach((box, index) => {
        box.addEventListener('input', () => {
            box.value = box.value.replace(/[^0-9]/g, '').slice(-1);
            sync();

            if (box.value && index < boxes.length - 1) {
                boxes[index + 1].focus();
            }
        });

        box.addEventListener('keydown', event => {
            if (event.key === 'Backspace' && !box.value && index > 0) {
                boxes[index - 1].value = '';
                sync();
                boxes[index - 1].focus();
            } else if (event.key === 'ArrowLeft' && index > 0) {
                boxes[index - 1].focus();
            } else if (event.key === 'ArrowRight' && index < boxes.length - 1) {
                boxes[index + 1].focus();
            }
        });

        box.addEventListener('paste', event => {
            const pasted = (event.clipboardData || window.clipboardData)
                .getData('text')
                .replace(/\D/g, '')
                .slice(0, 6);

            if (!pasted) return;

            event.preventDefault();

            boxes.forEach((item, i) => {
                item.value = pasted[i] || '';
            });

            sync();
            boxes[Math.min(pasted.length, 5)].focus();
        });
    });

    form.addEventListener('submit', event => {
        sync();

        if (!/^\d{6}$/.test(hidden.value)) {
            event.preventDefault();
            boxes.find(box => !box.value)?.focus();
        }
    });

    boxes[0]?.focus();
})();
