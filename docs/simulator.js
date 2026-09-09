(function () {
    "use strict";

    var FAN = {
        0: { temp: 36.0, rpm: 0, label: "LIVELLO 0 · SPENTA", cmd: "30 00 00 (STOP IO)", ack: "0x70 00 00 (ACK)", desc: "Ventola ferma. Il raffreddamento resta gestito dalla centralina di bordo." },
        1: { temp: 33.2, rpm: 1850, label: "LIVELLO 1 · SILENZIOSO", cmd: "30 81 01 (L1 CONTROL)", ack: "0x70 81 01 (POSITIVE ACK)", desc: "Flusso minimo, pensato per la guida urbana a basso carico." },
        2: { temp: 31.0, rpm: 2500, label: "LIVELLO 2 · MODERATO", cmd: "30 81 02 (L2 CONTROL)", ack: "0x70 81 02 (POSITIVE ACK)", desc: "Raffreddamento leggero per l'uso misto quotidiano." },
        3: { temp: 28.5, rpm: 3200, label: "LIVELLO 3 · BILANCIATO", cmd: "30 81 03 (L3 CONTROL)", ack: "0x70 81 03 (POSITIVE ACK)", desc: "Equilibrio tra silenziosità e smaltimento termico." },
        4: { temp: 27.0, rpm: 3700, label: "LIVELLO 4 · SPORT", cmd: "30 81 04 (L4 CONTROL)", ack: "0x70 81 04 (POSITIVE ACK)", desc: "Flusso sostenuto per una guida brillante." },
        5: { temp: 25.5, rpm: 4200, label: "LIVELLO 5 · TRACK", cmd: "30 81 05 (L5 CONTROL)", ack: "0x70 81 05 (POSITIVE ACK)", desc: "Raffreddamento aggressivo, pronto per l'uso intenso." },
        6: { temp: 24.2, rpm: 4680, label: "LIVELLO 6 · MAX", cmd: "30 81 06 (100% 12V)", ack: "0x70 81 06 (POSITIVE ACK)", desc: "Massima portata d'aria. Zero derating termico." }
    };

    var CELLS = [
        { name: "CELLA 1", temp: 24.1 },
        { name: "CELLA 2", temp: 24.6 },
        { name: "CELLA 3", temp: 23.8 },
        { name: "CELLA 4", temp: 24.9 }
    ];

    var mount = document.querySelector("[data-simulator]");
    if (!mount) { return; }

    mount.classList.add("simulator");
    mount.innerHTML =
        '<div class="sim-topline">' +
            '<span class="sim-brand">YARIS <b>HV</b> / COCKPIT</span>' +
            '<span class="micro">SIMULAZIONE WEB · v3.0.7</span>' +
        '</div>' +
        '<div class="sim-disclaimer">DEMO INTERATTIVA — Nessun collegamento Bluetooth, nessun comando inviato all\'auto. I valori sono illustrativi.</div>' +
        '<div class="sim-tabs" role="tablist" aria-label="Sezioni simulatore">' +
            '<button type="button" role="tab" aria-selected="true" data-tab="fan"><span>01</span> VENTOLA</button>' +
            '<button type="button" role="tab" aria-selected="false" data-tab="speed"><span>02</span> DRAGY</button>' +
            '<button type="button" role="tab" aria-selected="false" data-tab="cells"><span>03</span> CELLE</button>' +
        '</div>' +
        '<div class="sim-panel" data-panel="fan">' +
            '<div class="fan-dashboard">' +
                '<div>' +
                    '<span class="data-label">TEMP MAX CELLE</span>' +
                    '<p class="big-reading"><strong data-fan-temp>28.5</strong><small>°C</small></p>' +
                    '<div class="temperature-scale" data-temp-scale></div>' +
                    '<div class="scale-labels"><span>24°</span><span>32°</span><span>42°</span></div>' +
                '</div>' +
                '<div class="fan-center">' +
                    '<div class="fan-ring">' +
                        '<svg class="fan-rotor" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true">' +
                            '<circle cx="12" cy="12" r="3"/>' +
                            '<path d="M12 2C6.5 2 2 6.5 2 12c0 2.5 1 4.8 2.6 6.4L12 12V2z"/>' +
                            '<path d="M22 12c0-5.5-4.5-10-10-10-2.5 0-4.8 1-6.4 2.6L12 12h10z"/>' +
                            '<path d="M12 22c5.5 0 10-4.5 10-10 0-2.5-1-4.8-2.6-6.4L12 12v10z"/>' +
                            '<path d="M2 12c0 5.5 4.5 10 10 10 2.5 0 4.8-1 6.4-2.6L12 12H2z"/>' +
                        '</svg>' +
                    '</div>' +
                    '<strong data-fan-level>LIVELLO 3</strong>' +
                    '<p data-fan-rpm>3,200 RPM</p>' +
                '</div>' +
                '<div class="fan-controls">' +
                    '<span class="data-label">FORZATURA VENTOLA DENSO · ECU 7E2</span>' +
                    '<div class="level-buttons" role="group" aria-label="Livelli ventola">' +
                        '<button type="button" data-level="1" aria-pressed="false">L1</button>' +
                        '<button type="button" data-level="2" aria-pressed="false">L2</button>' +
                        '<button type="button" data-level="3" aria-pressed="true">L3</button>' +
                        '<button type="button" data-level="4" aria-pressed="false">L4</button>' +
                        '<button type="button" data-level="5" aria-pressed="false">L5</button>' +
                        '<button type="button" data-level="6" aria-pressed="false">L6 MAX</button>' +
                    '</div>' +
                    '<p class="level-description" data-fan-desc>' + FAN[3].desc + '</p>' +
                    '<p class="demo-event">COMANDO INVIATO · <span data-fan-cmd>' + FAN[3].cmd + '</span><br>ECU ACK · <span data-fan-ack>' + FAN[3].ack + '</span></p>' +
                '</div>' +
            '</div>' +
        '</div>' +
        '<div class="sim-panel" data-panel="speed" hidden>' +
            '<div class="speed-dashboard">' +
                '<div>' +
                    '<span class="data-label">VELOCITÀ SIMULATA · CAN LIVE</span>' +
                    '<p class="big-reading"><strong data-speed>0</strong><small>km/h</small></p>' +
                    '<div class="shift-lights" aria-hidden="true">' +
                        '<i></i><i></i><i></i><i></i><i></i><i></i><i></i><i></i><i></i><i></i>' +
                    '</div>' +
                    '<p class="run-status" data-run-status>PRONTO AL LANCIO</p>' +
                '</div>' +
                '<div>' +
                    '<span class="data-label">CRONOMETRO DRAGY · 0-100</span>' +
                    '<div class="timer-readings">' +
                        '<div><span class="data-label">0-50 KM/H</span><output data-t50>--.--s</output></div>' +
                        '<div><span class="data-label">0-100 KM/H</span><output data-t100>--.--s</output></div>' +
                    '</div>' +
                    '<div class="sim-actions">' +
                        '<button class="button" type="button" data-action="start">Avvia scatto</button>' +
                        '<button class="button button-outline" type="button" data-action="pause">Pausa</button>' +
                        '<button class="button button-outline" type="button" data-action="reset">Reset</button>' +
                    '</div>' +
                '</div>' +
            '</div>' +
        '</div>' +
        '<div class="sim-panel" data-panel="cells" hidden>' +
            '<span class="data-label">MATRICE 4 CELLE DENSO · PID 2228C1</span>' +
            '<div class="sensor-grid">' +
                CELLS.map(function (c) {
                    return '<div class="sensor"><div style="display:flex;justify-content:space-between;align-items:center;"><small>' + c.name + '</small><span class="demo-tag" style="font-size:8px;padding:1px 5px;">NOMINALE</span></div><output>' + c.temp.toFixed(1) + '°C</output></div>';
                }).join("") +
            '</div>' +
            '<p class="sensor-note">Valori simulati a scopo dimostrativo. La lettura reale dipende da vettura, adattatore e centralina.</p>' +
        '</div>' +
        '<div class="sim-footer">' +
            '<span>ECU 7E2 · DENSO HV BATTERY</span>' +
            '<span>SIMULATORE LOCALE · NESSUNA CONNESSIONE</span>' +
        '</div>';

    var tabs = Array.prototype.slice.call(mount.querySelectorAll(".sim-tabs button"));
    var panels = Array.prototype.slice.call(mount.querySelectorAll(".sim-panel"));
    var fanTempEl = mount.querySelector("[data-fan-temp]");
    var fanLevelEl = mount.querySelector("[data-fan-level]");
    var fanRpmEl = mount.querySelector("[data-fan-rpm]");
    var fanDescEl = mount.querySelector("[data-fan-desc]");
    var fanCmdEl = mount.querySelector("[data-fan-cmd]");
    var fanAckEl = mount.querySelector("[data-fan-ack]");
    var tempScaleEl = mount.querySelector("[data-temp-scale]");
    var rotorEl = mount.querySelector(".fan-rotor");
    var levelButtons = Array.prototype.slice.call(mount.querySelectorAll(".level-buttons button"));
    var speedEl = mount.querySelector("[data-speed]");
    var t50El = mount.querySelector("[data-t50]");
    var t100El = mount.querySelector("[data-t100]");
    var runStatusEl = mount.querySelector("[data-run-status]");
    var shiftLeds = Array.prototype.slice.call(mount.querySelectorAll(".shift-lights i"));
    var startBtn = mount.querySelector('[data-action="start"]');
    var pauseBtn = mount.querySelector('[data-action="pause"]');
    var resetBtn = mount.querySelector('[data-action="reset"]');

    var currentLevel = 3;
    var fanAnim = null;
    var simTimer = null;
    var running = false;
    var speed = 0;
    var rpm = 950;
    var t50 = null;
    var t100 = null;
    var startTime = 0;

    function setMarker(temp) {
        var pct = Math.max(0, Math.min(100, ((temp - 24) / (42 - 24)) * 100));
        tempScaleEl.style.setProperty("--marker", pct + "%");
    }

    function animateFanTemp(from, to) {
        if (fanAnim) { cancelAnimationFrame(fanAnim); }
        var start = performance.now();
        var duration = 1100;
        function step(now) {
            var t = Math.min(1, (now - start) / duration);
            var eased = 1 - Math.pow(1 - t, 3);
            var value = from + (to - from) * eased;
            fanTempEl.textContent = value.toFixed(1);
            setMarker(value);
            if (t < 1) { fanAnim = requestAnimationFrame(step); }
        }
        fanAnim = requestAnimationFrame(step);
    }

    function applyFanLevel(level, animate) {
        var cfg = FAN[level];
        currentLevel = level;
        levelButtons.forEach(function (btn) {
            btn.setAttribute("aria-pressed", String(Number(btn.getAttribute("data-level")) === level));
        });
        fanLevelEl.textContent = cfg.label;
        fanRpmEl.textContent = cfg.rpm === 0 ? "0 RPM" : cfg.rpm.toLocaleString("it-IT") + " RPM";
        fanDescEl.textContent = cfg.desc;
        fanCmdEl.textContent = cfg.cmd;
        fanAckEl.textContent = cfg.ack;
        /* Drive reactive CSS (airflow color/speed, battery glow) via attribute selector */
        if (level >= 1) {
            mount.setAttribute("data-active-level", String(level));
        } else {
            mount.removeAttribute("data-active-level");
        }
        if (cfg.rpm === 0) {
            rotorEl.style.animationPlayState = "paused";
        } else {
            rotorEl.style.animationDuration = Math.max(0.22, 2.0 - level * 0.28) + "s";
            rotorEl.style.animationPlayState = "running";
        }
        var current = parseFloat(fanTempEl.textContent) || cfg.temp;
        if (animate) {
            animateFanTemp(current, cfg.temp);
        } else {
            fanTempEl.textContent = cfg.temp.toFixed(1);
            setMarker(cfg.temp);
        }
    }

    function selectTab(name) {
        tabs.forEach(function (tab) {
            tab.setAttribute("aria-selected", String(tab.getAttribute("data-tab") === name));
        });
        panels.forEach(function (panel) {
            panel.hidden = panel.getAttribute("data-panel") !== name;
        });
    }

    function updateShiftLights(value) {
        var active = 0;
        if (value > 1200) {
            active = Math.min(shiftLeds.length, Math.floor(((value - 1200) / 4000) * shiftLeds.length));
        }
        shiftLeds.forEach(function (led, idx) {
            led.classList.toggle("on", idx < active);
        });
    }

    function renderSpeed() {
        speedEl.textContent = Math.floor(speed);
        updateShiftLights(rpm);
    }

    function resetRun() {
        if (simTimer) { clearInterval(simTimer); simTimer = null; }
        running = false;
        speed = 0;
        rpm = 950;
        t50 = null;
        t100 = null;
        t50El.textContent = "--.--s";
        t100El.textContent = "--.--s";
        runStatusEl.textContent = "PRONTO AL LANCIO";
        runStatusEl.style.color = "";
        startBtn.disabled = false;
        renderSpeed();
    }

    function tick() {
        var elapsed = (performance.now() - startTime) / 1000;
        if (speed < 50) {
            speed += 1.8;
            rpm = Math.min(5200, 2800 + Math.floor(speed * 45));
        } else if (speed < 100) {
            speed += 1.25;
            rpm = 5200;
        }
        if (speed >= 50 && t50 === null) {
            t50 = elapsed;
            t50El.textContent = elapsed.toFixed(2) + "s";
        }
        if (speed >= 100) {
            speed = 100;
            rpm = 4200;
            clearInterval(simTimer);
            simTimer = null;
            running = false;
            t100El.textContent = elapsed.toFixed(2) + "s";
            runStatusEl.textContent = "RECORD COMPLETATO";
            runStatusEl.style.color = "#83d9e3";
            startBtn.disabled = false;
        }
        renderSpeed();
    }

    tabs.forEach(function (tab) {
        tab.addEventListener("click", function () { selectTab(tab.getAttribute("data-tab")); });
    });

    levelButtons.forEach(function (btn) {
        btn.addEventListener("click", function () { applyFanLevel(Number(btn.getAttribute("data-level")), true); });
    });

    startBtn.addEventListener("click", function () {
        if (running) { return; }
        if (speed >= 100) { resetRun(); }
        running = true;
        startBtn.disabled = true;
        startTime = performance.now();
        runStatusEl.textContent = "FULL THROTTLE · SCATTO IN CORSO";
        runStatusEl.style.color = "#ee3342";
        simTimer = setInterval(tick, 50);
    });

    pauseBtn.addEventListener("click", function () {
        if (!running) { return; }
        clearInterval(simTimer);
        simTimer = null;
        running = false;
        startBtn.disabled = false;
        runStatusEl.textContent = "IN PAUSA";
        runStatusEl.style.color = "";
    });

    resetBtn.addEventListener("click", resetRun);

    applyFanLevel(3, false);
    renderSpeed();

    // Hero thermal scene (index page only).
    var scene = document.querySelector("[data-thermal-scene]");
    if (scene) {
        var heroTempEl = scene.querySelector("[data-hero-temp]");
        var heroStatusEl = scene.querySelector("[data-hero-status]");
        var heroToggle = scene.querySelector("[data-hero-toggle]");
        var cooling = false;
        var heroAnim = null;

        function animateHeroTemp(from, to) {
            if (heroAnim) { cancelAnimationFrame(heroAnim); }
            var start = performance.now();
            var duration = 1300;
            function step(now) {
                var t = Math.min(1, (now - start) / duration);
                var eased = 1 - Math.pow(1 - t, 3);
                heroTempEl.textContent = (from + (to - from) * eased).toFixed(1);
                if (t < 1) { heroAnim = requestAnimationFrame(step); }
            }
            heroAnim = requestAnimationFrame(step);
        }

        heroToggle.addEventListener("click", function () {
            cooling = !cooling;
            scene.classList.toggle("is-cooling", cooling);
            heroToggle.setAttribute("aria-pressed", String(cooling));
            heroToggle.innerHTML = cooling ? 'Torna a livello 3 <span aria-hidden="true">↗</span>' : 'Prova livello 6 <span aria-hidden="true">↗</span>';
            heroStatusEl.textContent = cooling ? "VENTILAZIONE L6" : "VENTILAZIONE L3";
            var current = parseFloat(heroTempEl.textContent) || 32.4;
            animateHeroTemp(current, cooling ? 24.2 : 32.4);
        });
    }
})();
