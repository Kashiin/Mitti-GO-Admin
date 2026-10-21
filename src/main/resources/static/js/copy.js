// Copy buttons: <button data-copy="text">. Shows a check for a moment; works without the Clipboard API too.
document.addEventListener('DOMContentLoaded', function () {
    document.querySelectorAll('[data-copy]').forEach(function (button) {
        button.addEventListener('click', function () {
            var text = button.getAttribute('data-copy');

            copy(text).then(function () {
                button.classList.add('copied');
                button.setAttribute('aria-label', 'Скопировано');
                setTimeout(function () {
                    button.classList.remove('copied');
                    button.setAttribute('aria-label', 'Скопировать код');
                }, 1500);
            });
        });
    });

    // The Clipboard API first; if the browser refuses it (permissions, embedded views), the old select-and-copy way.
    function copy(text) {
        if (navigator.clipboard && window.isSecureContext) {
            return navigator.clipboard.writeText(text).catch(function () { return fallback(text); });
        }

        return fallback(text);
    }

    function fallback(text) {
        var area = document.createElement('textarea');
        area.value = text;
        area.setAttribute('readonly', '');
        area.style.position = 'fixed';
        area.style.opacity = '0';
        document.body.appendChild(area);
        area.select();
        document.execCommand('copy');
        document.body.removeChild(area);

        return Promise.resolve();
    }
});
