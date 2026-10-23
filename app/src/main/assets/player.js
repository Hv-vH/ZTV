(function(forcePlay) {
    var video = document.querySelector('video');
    if (!video) return 'waiting';
    var player = window.__ztvPlayer;
    if (!player || player.video !== video) {
        player = {video: video, frameReady: false, frameRequested: false,
            playRequested: false, blocked: false};
        window.__ztvPlayer = player;

        var style = document.getElementById('cztv-tv-fullscreen-style');
        if (!style) {
            style = document.createElement('style');
            style.id = 'cztv-tv-fullscreen-style';
            (document.head || document.documentElement).appendChild(style);
        }
        style.textContent = 'html,body{margin:0!important;padding:0!important;'
            + 'width:100%!important;height:100%!important;overflow:hidden!important;'
            + 'background:#000!important;visibility:visible!important;}'
            + 'video{position:fixed!important;left:0!important;top:0!important;'
            + 'width:100vw!important;height:100vh!important;max-width:none!important;'
            + 'max-height:none!important;object-fit:contain!important;background:#000!important;'
            + 'visibility:visible!important;z-index:2147483647!important;}'
            + 'video::-webkit-media-controls{display:none!important;}'
            + 'video::-webkit-media-controls-enclosure{display:none!important;}';

        var path = video;
        while (path && path.parentElement) {
            var parent = path.parentElement;
            for (var i = 0; i < parent.children.length; i++) {
                if (parent.children[i] !== path) parent.children[i].style.display = 'none';
            }
            path = parent;
            if (parent === document.body) break;
        }
        path = video;
        while (path && path !== document.documentElement) {
            path.style.margin = '0';
            path.style.padding = '0';
            path.style.width = '100vw';
            path.style.height = '100vh';
            path.style.maxWidth = 'none';
            path.style.maxHeight = 'none';
            path.style.background = '#000';
            if (path !== video) {
                path.style.position = 'fixed';
                path.style.left = '0';
                path.style.top = '0';
            }
            path = path.parentElement;
        }
        video.style.display = 'block';
        video.setAttribute('playsinline', '');
        video.setAttribute('webkit-playsinline', '');
        function removeWebsiteControls() {
            if (video.controls) video.controls = false;
            if (video.hasAttribute('controls')) video.removeAttribute('controls');
            if (video.hasAttribute('poster')) video.removeAttribute('poster');
        }
        removeWebsiteControls();
        new MutationObserver(removeWebsiteControls).observe(video, {
            attributes: true, attributeFilter: ['controls', 'poster']
        });
        video.addEventListener('emptied', function() {
            player.frameReady = false;
            player.frameRequested = false;
            player.playRequested = false;
            player.blocked = false;
        });
    }

    if (forcePlay) {
        player.playRequested = false;
        player.blocked = false;
    }
    video.defaultMuted = false;
    video.muted = false;
    video.volume = 1;
    if (video.error) return 'error';
    if (player.blocked) return 'blocked';

    if (!player.frameReady && !player.frameRequested) {
        if (typeof video.requestVideoFrameCallback === 'function') {
            player.frameRequested = true;
            video.requestVideoFrameCallback(function() { player.frameReady = true; });
        } else if (video.readyState >= 2 && video.videoWidth > 0 && !video.paused) {
            // Older TV WebViews have no video-frame callback. Wait for decoded
            // data and two rendering opportunities before uncovering the video.
            player.frameRequested = true;
            requestAnimationFrame(function() {
                requestAnimationFrame(function() {
                    player.frameReady = video.readyState >= 2 && !video.paused;
                    player.frameRequested = false;
                });
            });
        }
    }
    if (!player.playRequested && video.paused) {
        player.playRequested = true;
        try {
            var promise = video.play();
            if (promise && promise.catch) promise.catch(function(error) {
                if (error && error.name === 'NotAllowedError') player.blocked = true;
                else player.playRequested = false;
            });
        } catch (error) {
            player.blocked = true;
        }
    }
    return player.frameReady && !video.paused && video.readyState >= 2
        && video.videoWidth > 0 ? 'playing' : 'pending';
})
