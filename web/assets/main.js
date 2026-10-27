/* AI Key Bridge — landing interactions. No dependencies. */
(function () {
  "use strict";

  var reduceMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;

  /* ---------- Nav: solid state after the top of the page leaves the viewport */
  var nav = document.getElementById("nav");
  var sentinel = document.getElementById("top");
  if (nav && sentinel && "IntersectionObserver" in window) {
    new IntersectionObserver(function (entries) {
      nav.classList.toggle("is-scrolled", !entries[0].isIntersecting);
    }, { threshold: 0 }).observe(sentinel);
  } else if (nav) {
    nav.classList.add("is-scrolled");
  }

  /* ---------- Mobile menu */
  var burger = document.getElementById("nav-burger");
  var links = document.getElementById("nav-links");
  function setMenu(open) {
    if (!burger || !links) return;
    burger.setAttribute("aria-expanded", String(open));
    burger.setAttribute("aria-label", open ? "Đóng menu" : "Mở menu");
    links.classList.toggle("is-open", open);
  }
  if (burger && links) {
    burger.addEventListener("click", function () {
      setMenu(burger.getAttribute("aria-expanded") !== "true");
    });
    links.addEventListener("click", function (event) {
      if (event.target.closest("a")) setMenu(false);
    });
    document.addEventListener("keydown", function (event) {
      if (event.key === "Escape") setMenu(false);
    });
    document.addEventListener("click", function (event) {
      if (!event.target.closest(".nav__links, #nav-burger")) setMenu(false);
    });
    window.matchMedia("(min-width: 901px)").addEventListener("change", function (event) {
      if (event.matches) setMenu(false);
    });
  }

  /* ---------- Reveal on scroll */
  var revealables = Array.prototype.slice.call(document.querySelectorAll("[data-reveal]"));
  if (reduceMotion || !("IntersectionObserver" in window)) {
    revealables.forEach(function (el) { el.classList.add("is-in"); });
  } else {
    var revealObserver = new IntersectionObserver(function (entries) {
      entries.forEach(function (entry) {
        if (entry.isIntersecting) {
          entry.target.classList.add("is-in");
          revealObserver.unobserve(entry.target);
        }
      });
    }, { rootMargin: "0px 0px -8% 0px", threshold: 0.12 });
    revealables.forEach(function (el) { revealObserver.observe(el); });
  }

  /* ---------- FAQ accordion */
  document.querySelectorAll(".faq__q").forEach(function (button) {
    button.addEventListener("click", function () {
      var open = button.getAttribute("aria-expanded") === "true";
      button.setAttribute("aria-expanded", String(!open));
      var item = button.closest(".faq__item");
      if (item) item.classList.toggle("is-open", !open);
    });
  });
  var firstAnswer = document.querySelector('.faq__q[aria-expanded="true"]');
  if (firstAnswer) {
    var item = firstAnswer.closest(".faq__item");
    if (item) item.classList.add("is-open");
  }

  /* ---------- Copy the provisioning commands */
  document.querySelectorAll("[data-copy-target]").forEach(function (button) {
    button.addEventListener("click", function () {
      var target = document.querySelector(button.getAttribute("data-copy-target"));
      if (!target) return;
      var text = target.innerText;
      var label = button.querySelector("span");
      var done = function () {
        button.classList.add("is-copied");
        if (label) label.textContent = "Đã copy";
        window.setTimeout(function () {
          button.classList.remove("is-copied");
          if (label) label.textContent = "Copy";
        }, 1800);
      };
      if (navigator.clipboard && window.isSecureContext) {
        navigator.clipboard.writeText(text).then(done, function () {});
      } else {
        var area = document.createElement("textarea");
        area.value = text;
        area.setAttribute("readonly", "");
        area.style.position = "fixed";
        area.style.opacity = "0";
        document.body.appendChild(area);
        area.select();
        try { document.execCommand("copy"); done(); } catch (e) { /* noop */ }
        document.body.removeChild(area);
      }
    });
  });

  /* ---------- Scroll-spy for the nav links */
  var navLinks = Array.prototype.slice.call(document.querySelectorAll(".nav__links a[href^='#']"));
  var sections = navLinks
    .map(function (a) { return document.querySelector(a.getAttribute("href")); })
    .filter(Boolean);
  if (sections.length && "IntersectionObserver" in window) {
    var visible = new Set();
    var spy = new IntersectionObserver(function (entries) {
      entries.forEach(function (entry) {
        if (entry.isIntersecting) visible.add(entry.target.id);
        else visible.delete(entry.target.id);
      });
      navLinks.forEach(function (a) {
        a.classList.toggle("is-current", visible.has(a.getAttribute("href").slice(1)));
      });
    }, { rootMargin: "-46% 0px -50% 0px" });
    sections.forEach(function (section) { spy.observe(section); });
  }

  /* ---------- Footer year */
  var year = document.getElementById("year");
  if (year) year.textContent = String(new Date().getFullYear());
})();
