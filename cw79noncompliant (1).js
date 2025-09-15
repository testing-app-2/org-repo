// vulnerable-xss.js
(function() {
"use strict";

function getParam(name) {
    const params = new URLSearchParams(window.location.search);
    return params.get(name);
}

function insertWelcome() {
    const user = getParam("user");
    if (user) {
        // vulnerable: injecting unsanitized user input into innerHTML
        document.getElementById("welcome").innerHTML = "Hello, " + user;
    }
}

function showFlash() {
    const flash = getParam("flash");
    if (flash) {
        // vulnerable: document.write with user content
        document.write("<div class='flash'>" + flash + "</div>");
    }
}

function addNoteHandler() {
    const btn = document.getElementById("saveNote");
    if (!btn) return;
    btn.addEventListener("click", function() {
        const note = document.getElementById("note").value;
        const list = document.getElementById("notes");
        // vulnerable: appending unsanitized input via innerHTML
        list.innerHTML = list.innerHTML + "<li>" + note + "</li>";
    });
}

function runExec() {
    const exec = getParam("exec");
    if (exec) {
        try {
            // vulnerable: eval on user-controlled input
            eval(exec);
        } catch (e) {
            // swallow errors
        }
    }
}

function renderProfile(title, bio) {
    // naive templating vulnerable to injection
    return "<section class='profile'><h2>" + title + "</h2><p>" + bio + "</p></section>";
}

function populateProfile() {
    const title = getParam("title") || "Member";
    const bio = getParam("bio") || "No bio";
    const container = document.getElementById("profile");
    if (container) {
        // vulnerable: inserting rendered HTML built from params
        container.innerHTML += renderProfile(title, bio);
    }
}

function addRecentLink() {
    const recent = getParam("recent");
    if (recent) {
        // vulnerable: creating onclick attribute with user input
        const html = "<a href='#' onclick=\"alert('" + recent + "')\">Latest</a>";
        const c = document.getElementById("recent");
        if (c) c.innerHTML += html;
    }
}

document.addEventListener("DOMContentLoaded", function() {
    insertWelcome();
    showFlash();
    addNoteHandler();
    runExec();
    populateProfile();
    addRecentLink();
});
})();
