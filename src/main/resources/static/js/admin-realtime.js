// MỘT kết nối STOMP dùng chung cho mọi script của trang quản trị (thông báo, biểu đồ...).
//
// Kết nối tới /admin/ws — endpoint nằm trong khu quản trị nên chỉ phiên admin bắt tay được.
// Không dùng SockJS: WebSocket thuần chỉ cần một GET Upgrade nên không vướng CSRF (xem WebSocketConfig).
//
// Cách dùng từ script khác:
//   tripgoRealtime.subscribe('/topic/xxx', function (payload) { ... });   // payload đã parse JSON
//   tripgoRealtime.onStatus(function (connected) { ... });
//
// subscribe gọi lúc nào cũng được, kể cả trước khi kết nối xong: đăng ký được ghi lại và tự đăng
// ký (lại) mỗi lần kết nối thành công — mất mạng rồi nối lại thì vẫn nhận tiếp, không phải F5.
(function () {
    const subscriptions = [];
    const statusListeners = [];
    let connected = false;

    function setConnected(value) {
        connected = value;
        statusListeners.forEach(function (listener) {
            listener(value);
        });
    }

    function attach(client, entry) {
        client.subscribe(entry.destination, function (frame) {
            try {
                entry.handler(JSON.parse(frame.body));
            } catch (error) {
                // Gói tin hỏng hay handler lỗi không được làm đứt luồng nhận các gói sau.
            }
        });
    }

    let client = null;
    if (typeof StompJs !== 'undefined') {
        const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
        client = new StompJs.Client({
            brokerURL: protocol + '//' + window.location.host + '/admin/ws',
            // Tự kết nối lại khi mạng chập chờn hoặc server khởi động lại.
            reconnectDelay: 5000,
            heartbeatIncoming: 10000,
            heartbeatOutgoing: 10000
        });
        client.onConnect = function () {
            subscriptions.forEach(function (entry) {
                attach(client, entry);
            });
            setConnected(true);
        };
        client.onWebSocketClose = function () {
            setConnected(false);
        };
        client.activate();
    }

    window.tripgoRealtime = {
        subscribe: function (destination, handler) {
            const entry = { destination: destination, handler: handler };
            subscriptions.push(entry);
            if (client && connected) {
                attach(client, entry);
            }
        },
        onStatus: function (listener) {
            statusListeners.push(listener);
            listener(connected);
        }
    };
})();
