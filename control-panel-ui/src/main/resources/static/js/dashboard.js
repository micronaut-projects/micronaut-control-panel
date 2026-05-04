(function ($) {
    "use strict";

    var modalBackdropClass = "modal-backdrop";
    var themeStorageKey = "control-panel-theme";
    var sidebarStorageKey = "control-panel-sidebar";
    var sidebarBreakpoint = 1000;

    function systemTheme() {
        return window.matchMedia && window.matchMedia("(prefers-color-scheme: light)").matches ? "light" : "dark";
    }

    function hasStoredTheme() {
        try {
            var theme = localStorage.getItem(themeStorageKey);
            return theme === "light" || theme === "dark";
        } catch (error) {
            return false;
        }
    }

    function readTheme() {
        var theme = document.documentElement.getAttribute("data-theme");
        if (theme === "light" || theme === "dark") {
            return theme;
        }
        return systemTheme();
    }

    function writeTheme(theme) {
        document.documentElement.setAttribute("data-theme", theme);
        try {
            localStorage.setItem(themeStorageKey, theme);
        } catch (error) {
            // Theme selection is still applied for the current page if storage is unavailable.
        }
    }

    function updateThemeButton() {
        var theme = readTheme();
        var button = document.getElementById("themeToggle");
        if (!button) {
            return;
        }
        var nextTheme = theme === "light" ? "dark" : "light";
        var label = "Switch to " + nextTheme + " mode";
        button.setAttribute("aria-label", label);
        button.setAttribute("title", label);
    }

    function syncThemeWithSystem() {
        if (!hasStoredTheme()) {
            document.documentElement.setAttribute("data-theme", systemTheme());
            updateThemeButton();
        }
    }

    function isMobileSidebar() {
        return window.matchMedia("(max-width: " + sidebarBreakpoint + "px)").matches;
    }

    function readSidebarState() {
        return document.documentElement.getAttribute("data-sidebar") === "collapsed" ? "collapsed" : "expanded";
    }

    function writeSidebarState(state) {
        if (state === "collapsed") {
            document.documentElement.setAttribute("data-sidebar", "collapsed");
        } else {
            document.documentElement.removeAttribute("data-sidebar");
        }
        try {
            localStorage.setItem(sidebarStorageKey, state);
        } catch (error) {
            // The sidebar state is still applied for the current page if storage is unavailable.
        }
    }

    function updateSidebarButtons() {
        var expanded = isMobileSidebar()
            ? document.body.classList.contains("cp-sidebar-open")
            : readSidebarState() !== "collapsed";
        $("[data-sidebar-toggle]").each(function () {
            this.setAttribute("aria-expanded", expanded ? "true" : "false");
            this.setAttribute("title", expanded ? "Collapse sidebar" : "Expand sidebar");
        });
    }

    function notifyLayoutChanged() {
        var notify = function () {
            window.dispatchEvent(new Event("resize"));
            if (window.codemirror && window.codemirror.view && typeof window.codemirror.view.requestMeasure === "function") {
                window.codemirror.view.requestMeasure();
            }
        };
        window.requestAnimationFrame(function () {
            notify();
            window.requestAnimationFrame(notify);
        });
        window.setTimeout(notify, 180);
    }

    function ensureBackdrop() {
        var existing = document.querySelector("." + modalBackdropClass);
        if (existing) {
            return existing;
        }
        var backdrop = document.createElement("button");
        backdrop.type = "button";
        backdrop.className = modalBackdropClass;
        backdrop.setAttribute("aria-label", "Close dialog");
        document.body.appendChild(backdrop);
        return backdrop;
    }

    function removeBackdropIfUnused() {
        if (document.querySelector(".modal.show")) {
            return;
        }
        var backdrop = document.querySelector("." + modalBackdropClass);
        if (backdrop) {
            backdrop.remove();
        }
        document.body.classList.remove("modal-open");
    }

    function showModal(element, relatedTarget) {
        var $modal = $(element);
        if ($modal.hasClass("show")) {
            return;
        }

        var showEvent = $.Event("show.bs.modal", { relatedTarget: relatedTarget || null });
        $modal.trigger(showEvent);
        if (showEvent.isDefaultPrevented()) {
            return;
        }

        ensureBackdrop();
        document.body.classList.add("modal-open");
        element.style.display = "block";
        element.removeAttribute("aria-hidden");
        element.setAttribute("aria-modal", "true");
        element.setAttribute("role", element.getAttribute("role") || "dialog");
        element.offsetHeight;
        $modal.addClass("show");

        var focusTarget = element.querySelector(".modal-content, button, [href], input, select, textarea, [tabindex]:not([tabindex='-1'])");
        if (focusTarget) {
            focusTarget.focus({ preventScroll: true });
        }

        $modal.trigger($.Event("shown.bs.modal", { relatedTarget: relatedTarget || null }));
    }

    function hideModal(element) {
        var $modal = $(element);
        if (!$modal.hasClass("show")) {
            return;
        }

        var hideEvent = $.Event("hide.bs.modal");
        $modal.trigger(hideEvent);
        if (hideEvent.isDefaultPrevented()) {
            return;
        }

        $modal.removeClass("show");
        element.setAttribute("aria-hidden", "true");
        element.removeAttribute("aria-modal");
        element.style.display = "none";
        removeBackdropIfUnused();
        $modal.trigger("hidden.bs.modal");
    }

    function pluralize(count, singular, plural) {
        return count === 1 ? singular : plural;
    }

    function parsePositiveInteger(value, fallback) {
        var parsed = parseInt(value, 10);
        return Number.isFinite(parsed) && parsed > 0 ? parsed : fallback;
    }

    function getFilterTablePageSize($table) {
        var configured = parsePositiveInteger($table.attr("data-filter-table-page-size"), 10);
        var $select = $table.find("[data-filter-table-page-size-select]").first();
        if ($select.length) {
            return parsePositiveInteger($select.val(), configured);
        }
        return configured;
    }

    function setFilterTablePage($table, page) {
        $table.attr("data-filter-table-current-page", String(page));
    }

    function closeActionMenus(except) {
        document.querySelectorAll("[data-actions-menu][open]").forEach(function (menu) {
            if (menu !== except) {
                menu.removeAttribute("open");
            }
        });
    }

    function positionActionMenu(menu) {
        if (!menu || !menu.open) {
            return;
        }
        var trigger = menu.querySelector("summary");
        var content = menu.querySelector(".cp-actions-menu-content");
        if (!trigger || !content) {
            return;
        }

        var spacing = 6;
        var viewportPadding = 8;
        var triggerRect = trigger.getBoundingClientRect();
        var contentWidth = content.offsetWidth || 176;
        var contentHeight = content.offsetHeight || 0;
        var left = triggerRect.right - contentWidth;
        var top = triggerRect.bottom + spacing;

        left = Math.max(viewportPadding, Math.min(left, window.innerWidth - contentWidth - viewportPadding));
        if (contentHeight > 0 && top + contentHeight > window.innerHeight - viewportPadding) {
            top = triggerRect.top - contentHeight - spacing;
        }
        top = Math.max(viewportPadding, top);

        menu.style.setProperty("--cp-actions-menu-left", Math.round(left) + "px");
        menu.style.setProperty("--cp-actions-menu-top", Math.round(top) + "px");
    }

    function positionOpenActionMenus() {
        document.querySelectorAll("[data-actions-menu][open]").forEach(positionActionMenu);
    }

    function positionHoverCard(trigger) {
        if (!trigger) {
            return;
        }
        var content = trigger.querySelector(".cp-hover-card-content");
        if (!content) {
            return;
        }

        var spacing = 8;
        var viewportPadding = 8;
        var triggerRect = trigger.getBoundingClientRect();
        var contentWidth = content.offsetWidth || 192;
        var contentHeight = content.offsetHeight || 0;
        var left = triggerRect.left;
        var top = triggerRect.bottom + spacing;

        left = Math.max(viewportPadding, Math.min(left, window.innerWidth - contentWidth - viewportPadding));
        if (contentHeight > 0 && top + contentHeight > window.innerHeight - viewportPadding) {
            top = triggerRect.top - contentHeight - spacing;
        }
        top = Math.max(viewportPadding, top);

        trigger.style.setProperty("--cp-hover-card-left", Math.round(left) + "px");
        trigger.style.setProperty("--cp-hover-card-top", Math.round(top) + "px");
    }

    function positionOpenHoverCards() {
        document.querySelectorAll("[data-hover-card]:hover, [data-hover-card]:focus-within").forEach(positionHoverCard);
    }

    function updateFilterTable(table) {
        var $table = $(table);
        var $rows = $table.find("[data-filter-table-row]");
        var query = String($table.find("[data-filter-table-search]").val() || "").trim().toLowerCase();
        var matchedRows = [];

        $rows.each(function () {
            var isMatch = query === "" || $(this).text().toLowerCase().indexOf(query) !== -1;
            if (isMatch) {
                matchedRows.push(this);
            }
        });

        var totalRows = $rows.length;
        var visibleRows = matchedRows.length;
        var singular = $table.attr("data-filter-table-label") || "row";
        var plural = $table.attr("data-filter-table-label-plural") || singular + "s";
        var unpaged = $table.is("[data-filter-table-unpaged]");

        if (unpaged) {
            $rows.each(function () {
                this.hidden = true;
            });
            matchedRows.forEach(function (row) {
                row.hidden = false;
            });

            var unpagedLabel = pluralize(visibleRows, singular, plural);
            var unpagedSummary = visibleRows + " " + unpagedLabel;
            if (query !== "" && visibleRows !== totalRows) {
                unpagedSummary += " filtered from " + totalRows;
            }
            $table.find("[data-filter-table-summary]").text(unpagedSummary);
            $table.find("[data-filter-table-empty]").prop("hidden", visibleRows !== 0);
            return;
        }

        var pageSize = getFilterTablePageSize($table);
        var totalPages = Math.max(1, Math.ceil(visibleRows / pageSize));
        var page = parsePositiveInteger($table.attr("data-filter-table-current-page"), 1);
        page = Math.min(Math.max(page, 1), totalPages);
        setFilterTablePage($table, page);

        var start = (page - 1) * pageSize;
        var end = start + pageSize;
        $rows.each(function () {
            this.hidden = true;
        });
        matchedRows.forEach(function (row, index) {
            row.hidden = index < start || index >= end;
        });

        var label = pluralize(visibleRows, singular, plural);
        var summary;
        if (visibleRows === 0) {
            summary = query === "" ? "0 " + plural : "0 of " + totalRows + " " + plural;
        } else {
            summary = "Showing " + (start + 1) + "-" + Math.min(end, visibleRows) + " of " + visibleRows + " " + label;
            if (query !== "" && visibleRows !== totalRows) {
                summary += " filtered from " + totalRows;
            }
        }

        $table.find("[data-filter-table-summary]").text(summary);
        $table.find("[data-filter-table-empty]").prop("hidden", visibleRows !== 0);
        $table.find("[data-filter-table-page]").text(visibleRows === 0 ? "Page 0 of 0" : "Page " + page + " of " + totalPages);
        $table.find("[data-filter-table-prev]").prop("disabled", visibleRows === 0 || page <= 1);
        $table.find("[data-filter-table-next]").prop("disabled", visibleRows === 0 || page >= totalPages);
        $table.find("[data-filter-table-page-size-select]").val(String(pageSize));
    }

    function bindModalEvents() {
        $(document).on("click", "[data-toggle='modal'], [data-dialog-target]", function (event) {
            event.preventDefault();
            var targetSelector = this.getAttribute("data-dialog-target") || this.getAttribute("data-target") || this.getAttribute("href");
            if (!targetSelector) {
                return;
            }
            var target = document.querySelector(targetSelector);
            if (target) {
                showModal(target, this);
            }
            var menu = this.closest("[data-actions-menu]");
            if (menu) {
                menu.removeAttribute("open");
            }
        });

        $(document).on("click", "[data-dismiss='modal'], [data-dialog-close]", function (event) {
            event.preventDefault();
            var modal = this.closest(".modal");
            if (modal) {
                hideModal(modal);
            }
        });

        $(document).on("click", "." + modalBackdropClass, function () {
            var modals = document.querySelectorAll(".modal.show");
            if (modals.length > 0) {
                hideModal(modals[modals.length - 1]);
            }
        });

        $(document).on("keydown", function (event) {
            if (event.key !== "Escape") {
                return;
            }
            var modals = document.querySelectorAll(".modal.show");
            if (modals.length > 0) {
                hideModal(modals[modals.length - 1]);
            }
        });
    }

    $.fn.modal = function (action, relatedTarget) {
        return this.each(function () {
            if (action === "hide") {
                hideModal(this);
            } else {
                showModal(this, relatedTarget);
            }
        });
    };

    window.globalAlertMessageReload = function (title, message, cssClass, iconClass) {
        sessionStorage.setItem("globalAlertTitle", title);
        sessionStorage.setItem("globalAlertMessage", message);
        sessionStorage.setItem("globalAlertClass", cssClass);
        sessionStorage.setItem("globalAlertIconClass", iconClass);
        location.reload();
    };

    window.globalAlertMessageDisplay = function (title, message, cssClass, iconClass) {
        $("#globalAlertTitle").text(title);
        $("#globalAlertMessage").text(message);

        $("#globalAlert").removeClass().addClass("alert").addClass(cssClass);
        $("#globalAlert i").removeClass().addClass("icon fas").addClass(iconClass);
        $("#globalAlert").show();
    };

    window.globalAlertMessageClear = function () {
        sessionStorage.clear();
        $("#globalAlertTitle").text("");
        $("#globalAlertMessage").text("");
        $("#globalAlert").removeClass();
        $("#globalAlert i").removeClass();
        $("#globalAlert").hide();
    };

    bindModalEvents();

    $(function () {
        var title = sessionStorage.getItem("globalAlertTitle");
        if (title !== null) {
            globalAlertMessageDisplay(
                title,
                sessionStorage.getItem("globalAlertMessage"),
                sessionStorage.getItem("globalAlertClass"),
                sessionStorage.getItem("globalAlertIconClass")
            );
            sessionStorage.clear();
        }

        $(document).on("click", function (event) {
            if (event.target.closest("[data-actions-menu]")) {
                positionOpenActionMenus();
                return;
            }
            closeActionMenus();
        });

        document.addEventListener("toggle", function (event) {
            var menu = event.target.closest ? event.target.closest("[data-actions-menu]") : null;
            if (!menu) {
                return;
            }
            if (menu.open) {
                closeActionMenus(menu);
                positionActionMenu(menu);
            }
        }, true);

        $(document).on("mouseenter focusin", "[data-hover-card]", function () {
            positionHoverCard(this);
        });

        window.addEventListener("resize", positionOpenActionMenus);
        window.addEventListener("scroll", positionOpenActionMenus, true);
        window.addEventListener("resize", positionOpenHoverCards);
        window.addEventListener("scroll", positionOpenHoverCards, true);

        $(document).on("click", "[data-card-widget='collapse']", function (event) {
            event.preventDefault();
            var $card = $(this).closest(".card");
            var $body = $card.children(".card-body");
            var $icon = $(this).find(".fas, .fa");
            $card.toggleClass("collapsed-card");
            $body.stop(true, true).slideToggle(120);
            $icon.toggleClass("fa-minus fa-plus");
        });

        $(document).on("click", "[data-sidebar-toggle]", function () {
            if (isMobileSidebar()) {
                document.body.classList.toggle("cp-sidebar-open");
            } else {
                writeSidebarState(readSidebarState() === "collapsed" ? "expanded" : "collapsed");
            }
            updateSidebarButtons();
            notifyLayoutChanged();
        });

        $(document).on("click", "[data-sidebar-close]", function () {
            document.body.classList.remove("cp-sidebar-open");
            updateSidebarButtons();
            notifyLayoutChanged();
        });

        $(window).on("resize", function () {
            if (!isMobileSidebar()) {
                document.body.classList.remove("cp-sidebar-open");
            }
            updateSidebarButtons();
        });

        if (window.matchMedia) {
            var colorScheme = window.matchMedia("(prefers-color-scheme: light)");
            if (typeof colorScheme.addEventListener === "function") {
                colorScheme.addEventListener("change", syncThemeWithSystem);
            } else if (typeof colorScheme.addListener === "function") {
                colorScheme.addListener(syncThemeWithSystem);
            }
        }

        syncThemeWithSystem();
        updateThemeButton();
        updateSidebarButtons();

        $(document).on("click", "#themeToggle", function () {
            writeTheme(readTheme() === "light" ? "dark" : "light");
            updateThemeButton();
        });

        $(document).on("click", "[data-tabs-trigger]", function () {
            var target = this.getAttribute("data-tabs-trigger");
            var $tabs = $(this).closest("[data-tabs]");
            $tabs.find("[data-tabs-trigger]").each(function () {
                var active = this.getAttribute("data-tabs-trigger") === target;
                this.classList.toggle("active", active);
                this.setAttribute("aria-selected", active ? "true" : "false");
            });
            $tabs.find("[data-tabs-panel]").each(function () {
                var active = this.getAttribute("data-tabs-panel") === target;
                this.hidden = !active;
                if (active) {
                    $(this).find("[data-filter-table]").each(function () {
                        updateFilterTable(this);
                    });
                }
            });
        });

        $("[data-filter-table]").each(function () {
            updateFilterTable(this);
        });

        $(document).on("input", "[data-filter-table-search]", function () {
            var $table = $(this).closest("[data-filter-table]");
            setFilterTablePage($table, 1);
            updateFilterTable($table);
        });

        $(document).on("change", "[data-filter-table-page-size-select]", function () {
            var $table = $(this).closest("[data-filter-table]");
            $table.attr("data-filter-table-page-size", this.value);
            setFilterTablePage($table, 1);
            updateFilterTable($table);
        });

        $(document).on("click", "[data-filter-table-prev], [data-filter-table-next]", function () {
            var $table = $(this).closest("[data-filter-table]");
            var current = parsePositiveInteger($table.attr("data-filter-table-current-page"), 1);
            var next = this.hasAttribute("data-filter-table-prev") ? current - 1 : current + 1;
            setFilterTablePage($table, next);
            updateFilterTable($table);
        });
    });
})(jQuery);
