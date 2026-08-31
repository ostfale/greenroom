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
