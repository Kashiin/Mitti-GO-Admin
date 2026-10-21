// Long lists scroll inside their card instead of stretching the page. Most show 10 items: the box is cut right after the
// 10th row (or table row, under the sticky header). A box marked `data-fill` (the change log) instead reaches down to the
// bottom of the window. Shorter lists keep their own height. Without JS the CSS cap (a screen's height) still applies.
(function () {
    var VISIBLE = 10;
    // Room left under a full-height box: the card's padding and the page's bottom margin.
    var FILL_BOTTOM_GAP = 48;
    var FILL_MIN_HEIGHT = 240;

    function items(box) {
        var body = box.querySelector('tbody');

        return body ? body.rows : box.children;
    }

    function fill(box) {
        var top = box.getBoundingClientRect().top + window.scrollY;

        box.style.maxHeight = Math.max(FILL_MIN_HEIGHT, window.innerHeight - top - FILL_BOTTOM_GAP) + 'px';
    }

    function fit(box) {
        if (box.hasAttribute('data-fill')) {
            fill(box);
            return;
        }

        var list = items(box);

        if (list.length <= VISIBLE) {
            box.style.maxHeight = 'none';
            return;
        }
        var last = list[VISIBLE - 1];
        var bottom = last.getBoundingClientRect().bottom - box.getBoundingClientRect().top + box.scrollTop;

        box.style.maxHeight = Math.ceil(bottom) + 'px';
    }

    function fitAll() {
        document.querySelectorAll('.rows-scroll, .table-scroll').forEach(fit);
    }

    document.addEventListener('DOMContentLoaded', fitAll);
    window.addEventListener('load', fitAll);
    window.addEventListener('resize', fitAll);
})();
