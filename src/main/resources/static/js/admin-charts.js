// Biểu đồ "Doanh thu theo ngày" trên dashboard. Dữ liệu lấy từ /admin/reports/revenue-daily và
// tải lại mỗi lần đổi tháng.
//
// Chỉ có MỘT chuỗi số liệu nên dùng một màu duy nhất: tô mỗi cột một màu khác khi
// chúng cùng ý nghĩa là gán màu theo thứ hạng chứ không theo dữ liệu, và làm người đọc tưởng
// màu mang thông tin gì đó.
(function () {
    const dailyCanvas = document.getElementById('dailyRevenue');
    const monthInput = document.getElementById('dailyMonth');
    if (!dailyCanvas || !monthInput || typeof Chart === 'undefined') {
        return;
    }

    // Đọc màu từ CSS custom property để bảng màu chỉ khai ở một chỗ (admin.css) và tự đổi theo
    // chế độ sáng/tối mà không phải sửa JS.
    const css = getComputedStyle(document.documentElement);
    const token = (name, fallback) => (css.getPropertyValue(name) || fallback).trim();

    const SERIES = token('--viz-series-1', '#2a78d6');
    const SURFACE = token('--viz-surface', '#ffffff');
    const INK = token('--viz-text-primary', '#0b0b0b');
    const MUTED = token('--viz-text-secondary', '#52514e');
    const GRID = token('--viz-grid', '#e5e7eb');

    const money = new Intl.NumberFormat('vi-VN');
    const formatMoney = (value) => money.format(Math.round(value)) + ' đ';

    // Nhãn trục phải NGẮN. "10.000.000" dài tới mức Chart.js phải xoay chéo cho vừa, và khi xoay
    // thì các nhãn chồng lên nhau — đọc không ra. Rút về "10tr" thì nằm ngang, không cần xoay.
    const compact = (value) => {
        if (value >= 1e9) {
            return money.format(Math.round(value / 1e8) / 10) + ' tỷ';
        }
        if (value >= 1e6) {
            return money.format(Math.round(value / 1e5) / 10) + ' tr';
        }
        return money.format(value);
    };

    Chart.defaults.font.family = getComputedStyle(document.body).fontFamily;
    Chart.defaults.color = MUTED;

    // Lưới và trục phải lùi về SAU dữ liệu: z âm để đường lưới không cắt ngang qua cột.
    // maxRotation 0 + autoSkip: thà bỏ bớt nhãn còn hơn xoay chéo rồi chồng lên nhau.
    const axisChrome = {
        grid: { color: GRID, drawTicks: false, z: -1 },
        border: { display: false },
        ticks: { padding: 8, maxRotation: 0, autoSkip: true, maxTicksLimit: 6 }
    };

    let dailyChart = null;
    let latestDailyRequest = 0;

    monthInput.addEventListener('change', () => {
        // Người dùng xoá trắng ô chọn tháng -> giữ nguyên biểu đồ đang xem.
        if (monthInput.value) {
            loadDaily(monthInput.value);
        }
    });
    loadDaily(monthInput.value);

    function loadDaily(month) {
        // Đổi tháng liên tục thì các response có thể về lệch thứ tự: chỉ vẽ kết quả của lần chọn
        // CUỐI, response trễ của lần trước bị bỏ qua.
        const requestId = ++latestDailyRequest;
        fetch('/admin/reports/revenue-daily?month=' + encodeURIComponent(month || ''),
            { headers: { Accept: 'application/json' } })
            .then((response) => {
                if (!response.ok) {
                    throw new Error('HTTP ' + response.status);
                }
                return response.json();
            })
            .then((data) => {
                if (requestId === latestDailyRequest) {
                    renderDaily(data);
                }
            })
            .catch(() => {
                if (requestId === latestDailyRequest) {
                    dailyCanvas.hidden = true;
                    document.getElementById('dailyError').hidden = false;
                    document.getElementById('dailyTotal').textContent = '';
                }
            });
    }

    function renderDaily(data) {
        // Server đổi tháng tương lai / sai định dạng về tháng hiện tại -> ô chọn hiện đúng tháng đó.
        monthInput.value = data.month;
        monthInput.max = data.maxMonth;

        dailyCanvas.hidden = false;
        document.getElementById('dailyError').hidden = true;
        document.getElementById('dailyTotal').textContent =
            'Tổng tháng ' + monthLabel(data.month) + ': ' + formatMoney(data.totalRevenue)
            + ' · ' + data.totalBookings + ' đơn';
        fillTable('dailyRevenueTable', data.days, (d) => [d.label, d.bookingCount, formatMoney(d.revenue)]);

        const labels = data.days.map((d) => String(d.day));
        const values = data.days.map((d) => d.revenue);

        // Đã có biểu đồ thì chỉ thay dữ liệu: cột chuyển động sang tháng mới thay vì nháy trắng.
        if (dailyChart) {
            dailyChart.data.labels = labels;
            dailyChart.data.datasets[0].data = values;
            dailyChart.$days = data.days;
            dailyChart.update();
            return;
        }

        dailyChart = new Chart(dailyCanvas, {
            type: 'bar',
            data: {
                labels,
                datasets: [{
                    label: 'Doanh thu',
                    data: values,
                    backgroundColor: SERIES,
                    borderRadius: { topLeft: 3, topRight: 3 },
                    borderSkipped: 'bottom',
                    borderColor: SURFACE,
                    borderWidth: { top: 0, left: 1, right: 1, bottom: 0 },
                    maxBarThickness: 24
                }]
            },
            options: baseOptions({
                scales: {
                    // 31 nhãn ngày không vừa hết: cho hiện tối đa 16 rồi tự bỏ bớt, không xoay chéo.
                    x: { ...axisChrome, grid: { display: false },
                         ticks: { ...axisChrome.ticks, maxTicksLimit: 16 } },
                    y: {
                        ...axisChrome,
                        beginAtZero: true,
                        ticks: { ...axisChrome.ticks, callback: compact }
                    }
                },
                tooltipTitle: (items) => 'Ngày ' + dailyChart.$days[items[0].dataIndex].label,
                tooltip: (item) => [
                    formatMoney(item.parsed.y),
                    dailyChart.$days[item.dataIndex].bookingCount + ' đơn'
                ]
            })
        });
        // Giữ danh sách ngày của lần vẽ hiện tại cho tooltip (đổi tháng thì thay theo ở trên).
        dailyChart.$days = data.days;
    }

    // "2026-09" -> "09/2026"
    function monthLabel(month) {
        const [year, mm] = month.split('-');
        return mm + '/' + year;
    }

    function baseOptions({ scales, tooltip, tooltipTitle }) {
        return {
            responsive: true,
            maintainAspectRatio: false,
            scales,
            plugins: {
                // Một chuỗi số liệu thì tiêu đề đã nói rõ nó là gì, không cần chú giải.
                legend: { display: false },
                tooltip: {
                    backgroundColor: INK,
                    padding: 10,
                    displayColors: false,
                    callbacks: tooltipTitle ? { title: tooltipTitle, label: tooltip } : { label: tooltip }
                }
            },
            // Vùng bắt hover rộng hơn chính cột, để không phải trỏ trúng từng pixel.
            interaction: { mode: 'index', intersect: false }
        };
    }

    function fillTable(id, rows, toCells) {
        const body = document.querySelector('#' + id + ' tbody');
        if (!body) {
            return;
        }
        body.innerHTML = '';
        rows.forEach((row) => {
            const tr = document.createElement('tr');
            toCells(row).forEach((value, index) => {
                const td = document.createElement('td');
                td.textContent = value;
                if (index > 0) {
                    td.className = 'num';
                }
                tr.appendChild(td);
            });
            body.appendChild(tr);
        });
    }
})();
