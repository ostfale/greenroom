/* The only script in the application, and it does one thing.
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
 * Hands the draft to the local mail client. Reads the fields at the moment of the click,
 * because the tile around them is swapped by the request that runs alongside — and a
 * mailto: does not navigate the page away, it only wakes the client.
 */
function openMailClient(form) {
    const to = recipientOf(form);
    if (!to) {
        return;
    }
    const subject = form.querySelector("[name=subject]").value;
    const body = form.querySelector("[name=body]").value;
    window.location.href = "mailto:" + encodeURIComponent(to)
        + "?subject=" + encodeURIComponent(subject)
        + "&body=" + encodeURIComponent(body);
}

/** Whichever select carries an address on the entry that is picked. */
function recipientOf(form) {
    for (const select of form.querySelectorAll("select")) {
        const picked = select.selectedOptions[0];
        if (picked && picked.dataset.email) {
            return picked.dataset.email;
        }
    }
    return "";
}
