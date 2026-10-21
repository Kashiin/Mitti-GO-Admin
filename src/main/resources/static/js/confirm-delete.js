// "Delete …?" dialog: forms marked data-confirm-delete ask first and fill the dialog's data-field slots from their data-*
// (a promo code: the code, its Pro period, how long it works, how many times it was used; an admin: the login). Without
// JavaScript the form just submits.
document.addEventListener('DOMContentLoaded', function () {
    var dialog = document.getElementById('confirm-delete');

    if (!dialog || typeof dialog.showModal !== 'function') return;
    var pending = null;

    document.querySelectorAll('form[data-confirm-delete]').forEach(function (form) {
        form.addEventListener('submit', function (event) {
            if (form.dataset.confirmed === 'true') return;
            event.preventDefault();
            pending = form;

            dialog.querySelectorAll('[data-field]').forEach(function (field) {
                field.textContent = form.dataset[field.dataset.field] || '';
            });
            // Options in the dialog (a promo code: "take its Pro back") start unticked each time.
            dialog.querySelectorAll('[data-option]').forEach(function (option) { option.checked = false; });
            dialog.showModal();
            dialog.querySelector('[data-action="cancel"]').focus();
        });
    });

    dialog.querySelector('[data-action="cancel"]').addEventListener('click', function () {
        dialog.close();
    });

    dialog.querySelector('[data-action="confirm"]').addEventListener('click', function () {
        if (!pending) return;
        dialog.querySelectorAll('[data-option]').forEach(function (option) {
            var input = pending.querySelector('input[name="' + option.dataset.option + '"]');

            if (input) input.value = option.checked ? 'true' : 'false';
        });
        pending.dataset.confirmed = 'true';
        dialog.close();
        pending.requestSubmit ? pending.requestSubmit() : pending.submit();
    });

    // A click on the dimmed backdrop closes it, like Cancel.
    dialog.addEventListener('click', function (event) {
        if (event.target === dialog) dialog.close();
    });

    dialog.addEventListener('close', function () {
        if (pending && pending.dataset.confirmed !== 'true') pending = null;
    });
});
