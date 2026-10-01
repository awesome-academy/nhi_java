// Nhận thông báo realtime về đơn đặt và hiện một thẻ nhỏ ở góc màn hình.
// Kết nối STOMP dùng chung nằm ở admin-realtime.js.
(function () {
    const HOST = document.getElementById('notification-host');
    if (!HOST || !window.tripgoRealtime) {
        return;
    }

    window.tripgoRealtime.subscribe('/topic/bookings', show);

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
