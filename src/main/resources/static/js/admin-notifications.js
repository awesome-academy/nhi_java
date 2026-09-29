// Nhận thông báo realtime về đơn đặt và hiện một thẻ nhỏ ở góc màn hình.
//
// Kết nối tới /admin/ws — endpoint nằm trong khu quản trị nên chỉ phiên admin bắt tay được.
// Không dùng SockJS: WebSocket thuần chỉ cần một GET Upgrade nên không vướng CSRF.
(function () {
    if (typeof StompJs === 'undefined') {
        return;
    }

    const HOST = document.getElementById('notification-host');
    if (!HOST) {
        return;
    }

    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    const client = new StompJs.Client({
        brokerURL: protocol + '//' + window.location.host + '/admin/ws',
        // Tự kết nối lại khi mạng chập chờn hoặc server khởi động lại.
        reconnectDelay: 5000,
        heartbeatIncoming: 10000,
        heartbeatOutgoing: 10000
    });

    client.onConnect = function () {
        client.subscribe('/topic/bookings', function (frame) {
            try {
                show(JSON.parse(frame.body));
            } catch (error) {
                // Gói tin hỏng không được làm đứt luồng nhận các gói sau.
            }
        });
    };

    client.activate();

    function show(event) {
        const card = document.createElement('div');
        // Tiền tố tg- để không đụng component .toast của Bootstrap (xem ghi chú trong admin.css).
        card.className = 'tg-notice toast-' + (event.kind || '').toLowerCase();
        // textContent chứ không innerHTML: nội dung có tên khách và tên tour do người dùng nhập.
        card.textContent = event.message || 'Có cập nhật đơn đặt';

        const link = document.createElement('a');
        link.href = '/admin/bookings';
        link.textContent = 'Xem';
        card.appendChild(link);

        HOST.appendChild(card);
        // Tự biến mất để thông báo không chồng chất che mất giao diện.
        setTimeout(function () {
            card.remove();
        }, 8000);
    }
})();
