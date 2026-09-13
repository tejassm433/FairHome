/*
 * Small progressive enhancements for the application forms.
 *
 * Both checks below also run on the server, which is the copy that counts. These exist so a clerk
 * keying in a hundred paper forms sees a mistyped ID immediately instead of after a round trip.
 */
(function () {
    'use strict';

    // Verhoeff, the same check-digit scheme the server uses for the national ID.
    var D = [
        [0, 1, 2, 3, 4, 5, 6, 7, 8, 9],
        [1, 2, 3, 4, 0, 6, 7, 8, 9, 5],
        [2, 3, 4, 0, 1, 7, 8, 9, 5, 6],
        [3, 4, 0, 1, 2, 8, 9, 5, 6, 7],
        [4, 0, 1, 2, 3, 9, 5, 6, 7, 8],
        [5, 9, 8, 7, 6, 0, 4, 3, 2, 1],
        [6, 5, 9, 8, 7, 1, 0, 4, 3, 2],
        [7, 6, 5, 9, 8, 2, 1, 0, 4, 3],
        [8, 7, 6, 5, 9, 3, 2, 1, 0, 4],
        [9, 8, 7, 6, 5, 4, 3, 2, 1, 0]
    ];
    var P = [
        [0, 1, 2, 3, 4, 5, 6, 7, 8, 9],
        [1, 5, 7, 6, 2, 8, 3, 0, 9, 4],
        [5, 8, 0, 3, 7, 9, 6, 1, 4, 2],
        [8, 9, 1, 6, 0, 4, 3, 5, 2, 7],
        [9, 4, 5, 3, 1, 2, 6, 8, 7, 0],
        [4, 2, 8, 6, 5, 7, 3, 9, 0, 1],
        [2, 7, 9, 3, 8, 0, 6, 4, 1, 5],
        [7, 0, 4, 6, 9, 1, 3, 2, 5, 8]
    ];

    function checksumValid(digits) {
        var c = 0;
        for (var i = 0; i < digits.length; i++) {
            var digit = digits.charCodeAt(digits.length - 1 - i) - 48;
            if (digit < 0 || digit > 9) {
                return false;
            }
            c = D[c][P[i % 8][digit]];
        }
        return c === 0;
    }

    var idInput = document.querySelector('[data-checksum-check]');
    var idHint = document.getElementById('nationalIdHint');
    var idHintDefault = idHint ? idHint.textContent : '';

    if (idInput && idHint) {
        idInput.addEventListener('input', function () {
            var digits = idInput.value.replace(/\D/g, '');
            if (digits.length === 0) {
                idInput.classList.remove('invalid');
                idHint.textContent = idHintDefault;
                idHint.style.color = '';
                return;
            }
            if (digits.length < 12) {
                idInput.classList.remove('invalid');
                idHint.textContent = (12 - digits.length) + ' more digit(s) to go.';
                idHint.style.color = '';
                return;
            }
            if (digits.length > 12) {
                idInput.classList.add('invalid');
                idHint.textContent = 'That is ' + digits.length + ' digits; a national ID has 12.';
                idHint.style.color = 'var(--danger)';
                return;
            }
            if (digits.charAt(0) === '0' || digits.charAt(0) === '1') {
                idInput.classList.add('invalid');
                idHint.textContent = 'A national ID never starts with 0 or 1. Please check the first digit.';
                idHint.style.color = 'var(--danger)';
                return;
            }
            if (checksumValid(digits)) {
                idInput.classList.remove('invalid');
                idHint.textContent = 'Checksum looks right.';
                idHint.style.color = 'var(--success)';
            } else {
                idInput.classList.add('invalid');
                idHint.textContent = 'The checksum does not add up, so at least one digit is wrong.';
                idHint.style.color = 'var(--danger)';
            }
        });
    }

    // Shows which published income band the typed income falls into, and highlights that row.
    var incomeInput = document.querySelector('[data-income]');
    var incomeHint = document.getElementById('incomeHint');
    var incomeHintDefault = incomeHint ? incomeHint.textContent : '';
    var bandRows = Array.prototype.slice.call(document.querySelectorAll('[data-band-code]'));

    if (incomeInput && incomeHint && bandRows.length) {
        incomeInput.addEventListener('input', function () {
            var value = parseFloat(incomeInput.value);
            bandRows.forEach(function (row) {
                row.style.background = '';
                row.style.boxShadow = '';
            });
            if (isNaN(value)) {
                incomeHint.textContent = incomeHintDefault;
                incomeHint.style.color = '';
                return;
            }
            var matched = null;
            bandRows.forEach(function (row) {
                var min = parseFloat(row.getAttribute('data-band-min'));
                var maxRaw = row.getAttribute('data-band-max');
                var max = maxRaw === '' ? Infinity : parseFloat(maxRaw);
                if (value >= min && value <= max) {
                    matched = row;
                }
            });
            if (matched) {
                matched.style.background = 'rgba(94, 234, 212, 0.09)';
                matched.style.boxShadow = 'inset 2px 0 0 var(--accent)';
                incomeHint.textContent = 'This income falls in the '
                    + matched.getAttribute('data-band-code') + ' category.';
                incomeHint.style.color = 'var(--accent)';
            } else {
                incomeHint.textContent = 'This income does not fall in any published band. '
                    + 'Please check the figure.';
                incomeHint.style.color = 'var(--warn)';
            }
        });
    }
})();
