// Theme for this browser only: "auto" follows the system, "light" / "dark" pin it. Loaded in <head>, so the page paints
// in the chosen theme from the start. Storage can be blocked (private mode): then it simply stays "auto".
(function () {
    var KEY = 'mitti-admin-theme';
    var root = document.documentElement;

    function read() {
        try { return localStorage.getItem(KEY) || 'auto'; } catch (e) { return 'auto'; }
    }

    function apply(choice) {
        if (choice === 'light' || choice === 'dark') root.setAttribute('data-theme', choice);
        else root.removeAttribute('data-theme');

        document.querySelectorAll('[data-theme-choice]').forEach(function (button) {
            button.setAttribute('aria-pressed', String(button.getAttribute('data-theme-choice') === choice));
        });
    }

    apply(read());

    document.addEventListener('DOMContentLoaded', function () {
        apply(read());

        document.querySelectorAll('[data-theme-choice]').forEach(function (button) {
            button.addEventListener('click', function () {
                var choice = button.getAttribute('data-theme-choice');

                try { localStorage.setItem(KEY, choice); } catch (e) { /* stays for this page only */ }
                apply(choice);
            });
        });
    });
})();
