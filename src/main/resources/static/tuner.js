/**
 * GuitarTuner - 吉他调音器核心模块
 * 基于 YIN 算法 + 截尾均值 + EMA 平滑 + 目标锁定
 * 支持麦克风/声卡输入，自动/手动选弦，5种调弦预设
 */

// ================= 调弦预设 =================
var TUNER_PRESETS = [
    { id:'standard', name:'标准调音 E A D G B E', strings:[
        {note:'E',  label:'⑥', freq:82.41},
        {note:'A',  label:'⑤', freq:110.00},
        {note:'D',  label:'④', freq:146.83},
        {note:'G',  label:'③', freq:196.00},
        {note:'B',  label:'②', freq:246.94},
        {note:'E',  label:'①', freq:329.63}
    ]},
    { id:'dropD', name:'Drop D', strings:[
        {note:'D',  label:'⑥', freq:73.42},
        {note:'A',  label:'⑤', freq:110.00},
        {note:'D',  label:'④', freq:146.83},
        {note:'G',  label:'③', freq:196.00},
        {note:'B',  label:'②', freq:246.94},
        {note:'E',  label:'①', freq:329.63}
    ]},
    { id:'openG', name:'Open G (D G D G B D)', strings:[
        {note:'D',  label:'⑥', freq:73.42},
        {note:'G',  label:'⑤', freq:98.00},
        {note:'D',  label:'④', freq:146.83},
        {note:'G',  label:'③', freq:196.00},
        {note:'B',  label:'②', freq:246.94},
        {note:'D',  label:'①', freq:293.66}
    ]},
    { id:'dadgad', name:'DADGAD', strings:[
        {note:'D',  label:'⑥', freq:73.42},
        {note:'A',  label:'⑤', freq:110.00},
        {note:'D',  label:'④', freq:146.83},
        {note:'G',  label:'③', freq:196.00},
        {note:'A',  label:'②', freq:220.00},
        {note:'D',  label:'①', freq:293.66}
    ]},
    { id:'halfDown', name:'降半音 Eb Ab Db Gb Bb Eb', strings:[
        {note:'E\u266D', label:'⑥', freq:77.78},
        {note:'A\u266D', label:'⑤', freq:103.83},
        {note:'D\u266D', label:'④', freq:138.59},
        {note:'G\u266D', label:'③', freq:185.00},
        {note:'B\u266D', label:'②', freq:233.08},
        {note:'E\u266D', label:'①', freq:311.13}
    ]}
];

// ================= 状态变量 =================
var tunerStream = null, tunerAnalyser = null, tunerRunning = false, tunerAudioCtx = null;
var tunerGainNode = null, tunerRaf = null;
var tunerHighpass = null, tunerLowpass = null;
var tunerSelectedChannel = -1;
var tunerDeviceList = [];
var tunerCurrentPreset = TUNER_PRESETS[0];
var tunerSelectedIdx = 5;
var tunerAutoDetect = true;
var tunerSmoothedFreq = 0;
var tunerFreqHistory = [];
var tunerLastAutoIdx = -1, tunerLastAutoSwitchTime = 0;
var tunerLastDisplayUpdate = 0;
var tunerLastVibrateTime = 0;

// ================= 常量配置 =================
var TUNER_AUTO_SWITCH_DEBOUNCE_MS = 300;
var TUNER_AUTO_SWITCH_MAX_CENTS = 30;
var TUNER_FREQ_HISTORY_SIZE = 15;
var TUNER_DISPLAY_THROTTLE_MS = 250;
var TUNER_YIN_THRESHOLD = 0.10;
var TUNER_MIN_FREQ = 60;
var TUNER_MAX_FREQ = 1000;

// ================= UI 渲染 =================
function renderTunerPresets() {
    var sel = document.getElementById('tunerPreset');
    sel.innerHTML = '';
    TUNER_PRESETS.forEach(function(p) {
        var opt = document.createElement('option');
        opt.value = p.id; opt.textContent = p.name;
        sel.appendChild(opt);
    });
    sel.value = tunerCurrentPreset.id;
}

function onTunerPresetChange() {
    var id = document.getElementById('tunerPreset').value;
    for (var i = 0; i < TUNER_PRESETS.length; i++) {
        if (TUNER_PRESETS[i].id === id) { tunerCurrentPreset = TUNER_PRESETS[i]; break; }
    }
    tunerSelectedIdx = tunerCurrentPreset.strings.length - 1;
    renderTunerStrings();
}

function renderTunerStrings() {
    var g = document.getElementById('tunerStrings');
    g.innerHTML = '';
    g.style.gridTemplateColumns = 'repeat(' + tunerCurrentPreset.strings.length + ', 1fr)';
    tunerCurrentPreset.strings.forEach(function(s, i) {
        var b = document.createElement('div');
        b.className = 'tuner-str-btn' + (i === tunerSelectedIdx ? ' active' : '');
        b.innerHTML = '<span class="tuner-str-note">' + s.note + '</span><span class="tuner-str-num">' + s.label + '</span>';
        b.onclick = (function(idx) {
            return function() { setTunerAutoDetect(false); selectTunerIdx(idx); };
        })(i);
        g.appendChild(b);
    });
}

function selectTunerIdx(i) {
    tunerSelectedIdx = i;
    var btns = document.querySelectorAll('.tuner-str-btn');
    for (var j = 0; j < btns.length; j++) btns[j].classList.toggle('active', j === i);
}

function setTunerAutoDetect(on) {
    tunerAutoDetect = on;
    document.getElementById('tunerModeAuto').classList.toggle('on', on);
    document.getElementById('tunerModeManual').classList.toggle('on', !on);
}

// ================= YIN 音高检测算法 =================
function yinPitchDetect(buffer, sampleRate, hintFreq) {
    var N = buffer.length;
    var halfN = N >> 1;
    var minTau, maxTau;
    if (hintFreq && hintFreq > 0) {
        var hintTau = sampleRate / hintFreq;
        minTau = Math.max(2, Math.floor(hintTau / 1.0595));
        maxTau = Math.min(Math.ceil(hintTau * 1.0595), halfN - 1);
    } else {
        minTau = Math.floor(sampleRate / TUNER_MAX_FREQ);
        maxTau = Math.min(Math.floor(sampleRate / TUNER_MIN_FREQ), halfN - 1);
    }
    if (maxTau <= minTau) return 0;

    var yin = new Float32Array(halfN);
    for (var tau = 0; tau < halfN; tau++) {
        var sum = 0;
        for (var i = 0; i < halfN; i++) {
            var d = buffer[i] - buffer[i + tau];
            sum += d * d;
        }
        yin[tau] = sum;
    }

    yin[0] = 1.0;
    var runningSum = 0;
    for (var t = 1; t < halfN; t++) {
        runningSum += yin[t];
        yin[t] = runningSum > 0 ? yin[t] * t / runningSum : 1.0;
    }

    var tauEstimate = -1;
    for (var tt = minTau; tt < maxTau; tt++) {
        if (yin[tt] < TUNER_YIN_THRESHOLD) {
            while (tt + 1 < maxTau && yin[tt + 1] < yin[tt]) tt++;
            tauEstimate = tt;
            break;
        }
    }
    if (tauEstimate === -1) return 0;

    var refinedTau = tauEstimate;
    if (tauEstimate > 0 && tauEstimate < halfN - 1) {
        var s0 = yin[tauEstimate - 1], s1 = yin[tauEstimate], s2 = yin[tauEstimate + 1];
        var denom = 2.0 * (2.0 * s1 - s2 - s0);
        if (denom !== 0) {
            var adj = (s2 - s0) / denom;
            if (isFinite(adj)) refinedTau = tauEstimate + adj;
        }
    }
    if (refinedTau < minTau || refinedTau > maxTau) return 0;
    var freq = sampleRate / refinedTau;
    if (freq < TUNER_MIN_FREQ || freq > TUNER_MAX_FREQ) return 0;
    return freq;
}

function tunerCentsDiff(detected, target) {
    if (detected <= 0 || target <= 0) return 0;
    return 1200 * Math.log(detected / target) / Math.log(2);
}

// ================= 核心控制 =================
async function toggleTuner() {
    var btn = document.getElementById('tunerBtn');
    if (tunerRunning) { stopTuner(); return; }
    
    if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
        var msg = '当前浏览器不支持音频录入。';
        if (location.protocol !== 'https:' && location.hostname !== 'localhost' && location.hostname !== '127.0.0.1') {
            msg += '\n\n需要 HTTPS 或 localhost 才能使用麦克风。';
        }
        alert(msg);
        return;
    }

    try {
        var tempStream = await navigator.mediaDevices.getUserMedia({ audio: true });
        tempStream.getTracks().forEach(function(t) { t.stop(); });

        var deviceId = document.getElementById('tunerDevice').value;
        var audioConstraints = {
            echoCancellation: false,
            noiseSuppression: false,
            autoGainControl: false
        };
        if (deviceId) audioConstraints.deviceId = { exact: deviceId };
        tunerStream = await navigator.mediaDevices.getUserMedia({ audio: audioConstraints });

        tunerAudioCtx = new (window.AudioContext || window.webkitAudioContext)();
        await tunerAudioCtx.resume();
        var source = tunerAudioCtx.createMediaStreamSource(tunerStream);

        var trackSettings = tunerStream.getAudioTracks()[0].getSettings();
        var channelCount = trackSettings.channelCount || 1;

        tunerAnalyser = tunerAudioCtx.createAnalyser();
        tunerAnalyser.fftSize = 4096;
        tunerAnalyser.smoothingTimeConstant = 0;

        tunerGainNode = tunerAudioCtx.createGain();
        tunerGainNode.gain.value = 20;

        tunerHighpass = tunerAudioCtx.createBiquadFilter();
        tunerHighpass.type = 'highpass';
        tunerHighpass.frequency.value = 60;
        tunerHighpass.Q.value = 0.707;

        tunerLowpass = tunerAudioCtx.createBiquadFilter();
        tunerLowpass.type = 'lowpass';
        tunerLowpass.frequency.value = 1500;
        tunerLowpass.Q.value = 0.707;

        if (tunerSelectedChannel >= 0 && channelCount > 1) {
            var splitter = tunerAudioCtx.createChannelSplitter(channelCount);
            source.connect(splitter);
            var merger = tunerAudioCtx.createChannelMerger(1);
            splitter.connect(merger, Math.min(tunerSelectedChannel, channelCount - 1), 0);
            merger.connect(tunerGainNode);
        } else {
            source.connect(tunerGainNode);
        }
        tunerGainNode.connect(tunerHighpass);
        tunerHighpass.connect(tunerLowpass);
        tunerLowpass.connect(tunerAnalyser);

        tunerFreqHistory = [];
        tunerSmoothedFreq = 0;
        tunerLastDisplayUpdate = 0;
        tunerRunning = true;
        btn.textContent = '停止调音';
        btn.classList.add('active');
        detectPitch();

        renderChannels(channelCount);
    } catch (err) {
        var errMsg = '无法访问音频设备';
        if (err.name === 'NotAllowedError') {
            errMsg = '麦克风权限被拒绝，请在浏览器设置中允许访问麦克风';
        } else if (err.name === 'NotFoundError') {
            errMsg = '未找到音频输入设备，请检查麦克风或声卡连接';
        } else if (err.name === 'NotReadableError') {
            errMsg = '音频设备被其他程序占用，请关闭其他使用音频的程序后重试';
        } else {
            errMsg += ': ' + err.message;
        }
        alert(errMsg);
    }
}

function stopTuner() {
    tunerRunning = false;
    if (tunerRaf) { cancelAnimationFrame(tunerRaf); tunerRaf = null; }
    if (tunerStream) { tunerStream.getTracks().forEach(function(t) { t.stop(); }); }
    tunerStream = null;
    if (tunerAnalyser) { tunerAnalyser.disconnect(); tunerAnalyser = null; }
    if (tunerHighpass) { tunerHighpass.disconnect(); tunerHighpass = null; }
    if (tunerLowpass) { tunerLowpass.disconnect(); tunerLowpass = null; }
    if (tunerGainNode) { tunerGainNode.disconnect(); tunerGainNode = null; }
    if (tunerAudioCtx) { tunerAudioCtx.close(); tunerAudioCtx = null; }
    tunerSmoothedFreq = 0;
    tunerFreqHistory = [];
    var btn = document.getElementById('tunerBtn');
    btn.textContent = '开始调音';
    btn.classList.remove('active');
    document.getElementById('tunerNote').innerHTML = '-';
    document.getElementById('tunerStatus').textContent = '选择输入源后点击开始';
    document.getElementById('tunerStatus').className = 'tuner-status-text';
    document.getElementById('tunerFrequency').textContent = '-- Hz';
    document.getElementById('tunerIndicator').style.left = '50%';
    document.getElementById('tunerIndicator').className = 'tuner-indicator';
}

async function refreshDeviceList() {
    try {
        var tempStream = await navigator.mediaDevices.getUserMedia({ audio: true });
        tempStream.getTracks().forEach(function(t) { t.stop(); });
        var devices = await navigator.mediaDevices.enumerateDevices();
        tunerDeviceList = devices.filter(function(d) { return d.kind === 'audioinput'; });
        var select = document.getElementById('tunerDevice');
        var currentVal = select.value;
        select.innerHTML = '';
        var defOpt = document.createElement('option');
        defOpt.value = '';
        defOpt.textContent = '默认麦克风';
        select.appendChild(defOpt);
        tunerDeviceList.forEach(function(d, i) {
            var opt = document.createElement('option');
            opt.value = d.deviceId;
            opt.textContent = d.label || ('音频设备 ' + (i + 1));
            select.appendChild(opt);
        });
        if (currentVal) select.value = currentVal;
        document.getElementById('tunerDeviceCount').textContent = tunerDeviceList.length + ' 个设备';
    } catch (err) {
        console.error('枚举设备失败:', err);
        document.getElementById('tunerDeviceCount').textContent = '获取失败';
    }
}

function onTunerDeviceChange() {
    if (tunerRunning) { stopTuner(); toggleTuner(); }
}

function renderChannels(count) {
    var container = document.getElementById('tunerChannels');
    if (!container) return;
    container.innerHTML = '';
    if (count <= 1) {
        container.innerHTML = '<span style="color:#999;font-size:12px;">单声道设备</span>';
        tunerSelectedChannel = -1;
        return;
    }
    var allBtn = document.createElement('button');
    allBtn.className = 'tuner-ch-btn' + (tunerSelectedChannel === -1 ? ' active' : '');
    allBtn.textContent = '混合';
    allBtn.onclick = function() {
        tunerSelectedChannel = -1;
        document.querySelectorAll('.tuner-ch-btn').forEach(function(b) { b.classList.remove('active'); });
        allBtn.classList.add('active');
        if (tunerRunning) { stopTuner(); toggleTuner(); }
    };
    container.appendChild(allBtn);
    for (var i = 0; i < count; i++) {
        (function(ch) {
            var btn = document.createElement('button');
            btn.className = 'tuner-ch-btn' + (tunerSelectedChannel === ch ? ' active' : '');
            btn.textContent = 'CH' + (ch + 1);
            btn.onclick = function() {
                tunerSelectedChannel = ch;
                document.querySelectorAll('.tuner-ch-btn').forEach(function(b) { b.classList.remove('active'); });
                btn.classList.add('active');
                if (tunerRunning) { stopTuner(); toggleTuner(); }
            };
            container.appendChild(btn);
        })(i);
    }
}

// 兼容函数（已移除旧功能）
function onTunerTypeChange() {}
function enumerateDevices() { refreshDeviceList(); }

// ================= 核心检测循环 =================
function detectPitch() {
    if (!tunerRunning || !tunerAnalyser) return;

    var buffer = new Float32Array(tunerAnalyser.fftSize);
    tunerAnalyser.getFloatTimeDomainData(buffer);

    var rms = 0;
    for (var i = 0; i < buffer.length; i++) rms += buffer[i] * buffer[i];
    rms = Math.sqrt(rms / buffer.length);

    var noteEl = document.getElementById('tunerNote');
    var freqEl = document.getElementById('tunerFrequency');
    var statusEl = document.getElementById('tunerStatus');
    var indicator = document.getElementById('tunerIndicator');

    if (rms < 0.015) {
        tunerSmoothedFreq = 0;
        tunerFreqHistory = [];
        var target0 = tunerCurrentPreset.strings[tunerSelectedIdx];
        noteEl.innerHTML = target0.note;
        freqEl.textContent = '目标 ' + target0.freq.toFixed(1) + ' Hz';
        statusEl.textContent = '\ud83c\udfa4 请拨响琴弦';
        statusEl.className = 'tuner-status-text';
        indicator.className = 'tuner-indicator';
        noteEl.classList.remove('in-tune');
        indicator.style.left = '50%';
        tunerRaf = requestAnimationFrame(detectPitch);
        return;
    }

    var hintFreq = tunerAutoDetect ? 0 : tunerCurrentPreset.strings[tunerSelectedIdx].freq;
    var rawFreq = yinPitchDetect(buffer, tunerAudioCtx.sampleRate, hintFreq);

    if (rawFreq > 0) {
        tunerFreqHistory.push(rawFreq);
        if (tunerFreqHistory.length > TUNER_FREQ_HISTORY_SIZE) tunerFreqHistory.shift();
        var sorted = tunerFreqHistory.slice().sort(function(a, b) { return a - b; });
        var trimCount = sorted.length >= 9 ? 3 : (sorted.length >= 5 ? 1 : 0);
        var trimmed = sorted.slice(trimCount, sorted.length - trimCount);
        var sum = 0;
        for (var ti = 0; ti < trimmed.length; ti++) sum += trimmed[ti];
        var medianFreq = sum / trimmed.length;

        var jumpCents = tunerSmoothedFreq > 0 ? Math.abs(tunerCentsDiff(medianFreq, tunerSmoothedFreq)) : 0;
        var alpha = jumpCents > 80 ? 0.3 : 0.05;
        tunerSmoothedFreq = tunerSmoothedFreq === 0 ? medianFreq : tunerSmoothedFreq * (1 - alpha) + medianFreq * alpha;

        if (tunerAutoDetect) {
            var best = tunerSelectedIdx, minC = Infinity;
            tunerCurrentPreset.strings.forEach(function(s, idx) {
                var c = Math.abs(tunerCentsDiff(tunerSmoothedFreq, s.freq));
                if (c < minC) { minC = c; best = idx; }
            });
            var nowA = performance.now();
            if (minC < TUNER_AUTO_SWITCH_MAX_CENTS) {
                if (best !== tunerSelectedIdx) {
                    if (tunerLastAutoIdx === best) {
                        if (nowA - tunerLastAutoSwitchTime > TUNER_AUTO_SWITCH_DEBOUNCE_MS) {
                            selectTunerIdx(best);
                            tunerLastAutoSwitchTime = nowA;
                        }
                    } else {
                        tunerLastAutoIdx = best;
                        tunerLastAutoSwitchTime = nowA;
                    }
                } else {
                    tunerLastAutoIdx = best;
                    tunerLastAutoSwitchTime = nowA;
                }
            }
        }

        var target = tunerCurrentPreset.strings[tunerSelectedIdx];
        var cents = tunerCentsDiff(tunerSmoothedFreq, target.freq);
        var hzDiff = tunerSmoothedFreq - target.freq;

        indicator.style.left = Math.max(2, Math.min(98, 50 + (cents / 50) * 48)) + '%';

        var now = performance.now();
        if (now - tunerLastDisplayUpdate > TUNER_DISPLAY_THROTTLE_MS) {
            tunerLastDisplayUpdate = now;
            noteEl.innerHTML = target.note;
            freqEl.textContent = tunerSmoothedFreq.toFixed(1) + ' Hz · 目标 ' + target.freq.toFixed(1);
        }

        if (Math.abs(hzDiff) <= 0.5) {
            statusEl.textContent = '\u2713 完美音准';
            statusEl.className = 'tuner-status-text perfect';
            indicator.className = 'tuner-indicator perfect';
            noteEl.classList.add('in-tune');
            if (navigator.vibrate && now - tunerLastVibrateTime > 800) {
                navigator.vibrate(30);
                tunerLastVibrateTime = now;
            }
        } else {
            statusEl.textContent = '';
            statusEl.className = 'tuner-status-text';
            indicator.className = 'tuner-indicator';
            noteEl.classList.remove('in-tune');
        }
    } else {
        var nowN = performance.now();
        if (nowN - tunerLastDisplayUpdate > TUNER_DISPLAY_THROTTLE_MS) {
            freqEl.textContent = '\ud83d\udd0d 识别中…';
            tunerLastDisplayUpdate = nowN;
        }
    }

    tunerRaf = requestAnimationFrame(detectPitch);
}
