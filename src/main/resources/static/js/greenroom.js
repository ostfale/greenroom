/* The only script in the application, and it does very little.
 *
 * After a form was sent, the disclosure it sits in should fold away and the fields should
 * empty — but only if the answer did not carry a complaint. When it did, what was typed
 * has to stay where it is, or the correction starts from nothing.
 */
function closeUnlessRefused(form, listSelector) {
    if (document.querySelector(listSelector + " p.error")) {
        return;
    }
    form.reset();
    const disclosure = form.closest("details");
    if (disclosure) {
        disclosure.open = false;
    }
}

/**
 * Puts the text of the element that button points at on the clipboard, and says so for a
 * moment. Two ways, because the modern one exists only in a secure context: over plain
 * http — which is how this is reached in a home network — navigator.clipboard is undefined
 * and the old selection dance is the only one there is.
 */
function copyToClipboard(button) {
    const text = document.querySelector(button.dataset.copy).textContent;
    if (navigator.clipboard) {
        navigator.clipboard.writeText(text).then(() => confirmCopy(button));
        return;
    }
    const carrier = document.createElement("textarea");
    carrier.value = text;
    document.body.appendChild(carrier);
    carrier.select();
    document.execCommand("copy");
    carrier.remove();
    confirmCopy(button);
}

/** The button says what happened and goes back to what it is. */
function confirmCopy(button) {
    const label = button.textContent;
    button.textContent = button.dataset.copied;
    setTimeout(() => (button.textContent = label), 1500);
}

/* A save button that is still lit after saving cannot say whether it was pressed, and a
 * change made afterwards looks saved because the page looks the same. So the forms marked
 * `guarded` come out of the template with their save switched off: the first change to a
 * field switches it on, and the answer htmx swaps back in brings a dark one with it.
 *
 * Delegated from the document, because those forms are replaced whole and a listener bound
 * to one of them would be thrown away with it.
 */
document.addEventListener("input", unlockSaving);
document.addEventListener("change", unlockSaving);

function unlockSaving(event) {
    const field = event.target;
    const form = field.closest ? field.closest("form.guarded") : null;
    if (form) {
        form.querySelectorAll("button[type=submit]").forEach(button => (button.disabled = false));
    }
}
