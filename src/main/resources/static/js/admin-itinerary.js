// Thêm/xoá dòng lịch trình ngay trên form, không phải tải lại trang.
//
// Spring bind danh sách theo chỉ số liên tục (itinerary[0], itinerary[1]...). Xoá một dòng ở giữa
// mà không đánh lại tên trường sẽ làm đứt quãng chỉ số và các dòng sau bị bỏ qua khi lưu.
(function () {
    const rows = document.getElementById('itinerary-rows');
    const addButton = document.getElementById('add-day');
    if (!rows || !addButton) {
        return;
    }

    function renumber() {
        Array.from(rows.querySelectorAll('.itinerary-row')).forEach(function (row, index) {
            row.dataset.index = index;
            row.querySelector('.day-label').textContent = 'Ngày ' + (index + 1);

            row.querySelectorAll('[name]').forEach(function (field) {
                // itinerary[3].title -> itinerary[0].title
                field.name = field.name.replace(/itinerary\[\d+\]/, 'itinerary[' + index + ']');
                field.id = field.name.replace(/[\[\].]/g, '');
            });
        });
    }

    function createRow() {
        const index = rows.querySelectorAll('.itinerary-row').length;
        const row = document.createElement('div');
        row.className = 'itinerary-row';
        row.dataset.index = index;
        row.innerHTML =
            '<span class="day-label">Ngày ' + (index + 1) + '</span>' +
            '<input type="text" name="itinerary[' + index + '].title" ' +
            'placeholder="Tiêu đề trong ngày" maxlength="200">' +
            '<textarea name="itinerary[' + index + '].description" ' +
            'placeholder="Nội dung" rows="2"></textarea>' +
            '<button type="button" class="btn-link danger remove-day">Xoá</button>';
        return row;
    }

    addButton.addEventListener('click', function () {
        rows.appendChild(createRow());
    });

    // Uỷ quyền sự kiện: dòng thêm bằng JS cũng xoá được mà không phải gắn listener riêng.
    rows.addEventListener('click', function (event) {
        if (!event.target.classList.contains('remove-day')) {
            return;
        }
        event.target.closest('.itinerary-row').remove();
        renumber();
    });
})();
