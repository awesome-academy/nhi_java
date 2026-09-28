// Hai biểu đồ trên dashboard. Dữ liệu lấy từ /admin/reports/charts.
//
// Mỗi biểu đồ chỉ có MỘT chuỗi số liệu nên dùng một màu duy nhất: tô mỗi cột một màu khác khi
// chúng cùng ý nghĩa là gán màu theo thứ hạng chứ không theo dữ liệu, và làm người đọc tưởng
// màu mang thông tin gì đó.
(function () {
    const trendCanvas = document.getElementById('revenueTrend');
    const topCanvas = document.getElementById('topTours');
    if (!trendCanvas || !topCanvas || typeof Chart === 'undefined') {
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

    fetch('/admin/reports/charts', { headers: { Accept: 'application/json' } })
        .then((response) => {
            if (!response.ok) {
                throw new Error('HTTP ' + response.status);
            }
            return response.json();
        })
        .then((data) => {
            renderTrend(data.revenueByMonth || []);
            renderTopTours(data.topTours || []);
        })
        .catch(() => {
            // Hỏng dữ liệu thì ẩn biểu đồ đi, không để lại khung trắng khó hiểu.
            document.querySelectorAll('.chart-box').forEach((box) => {
                box.innerHTML = '<p class="chart-empty">Không tải được dữ liệu biểu đồ</p>';
            });
        });

    function renderTrend(points) {
        fillTable('revenueTrendTable', points, (p) => [p.label, p.bookingCount, formatMoney(p.revenue)]);

        new Chart(trendCanvas, {
            type: 'bar',
            data: {
                labels: points.map((p) => p.label),
                datasets: [{
                    label: 'Doanh thu',
                    data: points.map((p) => p.revenue),
                    backgroundColor: SERIES,
                    // Bo tròn đầu cột (đầu mang dữ liệu), chân cột giữ vuông vì nó neo vào trục 0.
                    borderRadius: { topLeft: 4, topRight: 4 },
                    borderSkipped: 'bottom',
                    // Khe 2px màu nền giữa các cột, thay cho việc vẽ viền quanh cột.
                    borderColor: SURFACE,
                    borderWidth: { top: 0, left: 1, right: 1, bottom: 0 },
                    maxBarThickness: 48
                }]
            },
            options: baseOptions({
                scales: {
                    x: { ...axisChrome, grid: { display: false } },
                    y: {
                        ...axisChrome,
                        beginAtZero: true,
                        ticks: { ...axisChrome.ticks, callback: compact }
                    }
                },
                tooltip: (item) => [
                    formatMoney(item.parsed.y),
                    item.raw !== null ? points[item.dataIndex].bookingCount + ' đơn' : ''
                ]
            })
        });
    }

    function renderTopTours(tours) {
        fillTable('topToursTable', tours, (t) => [t.tourTitle, formatMoney(t.revenue)]);

        if (tours.length === 0) {
            topCanvas.hidden = true;
            document.getElementById('topToursEmpty').hidden = false;
            return;
        }

        new Chart(topCanvas, {
            type: 'bar',
            data: {
                labels: tours.map((t) => t.tourTitle),
                datasets: [{
                    label: 'Doanh thu',
                    data: tours.map((t) => t.revenue),
                    backgroundColor: SERIES,
                    borderRadius: { topRight: 4, bottomRight: 4 },
                    borderSkipped: 'left',
                    borderColor: SURFACE,
                    borderWidth: { top: 1, bottom: 1 },
                    maxBarThickness: 28
                }]
            },
            options: baseOptions({
                indexAxis: 'y',
                scales: {
                    x: {
                        ...axisChrome,
                        beginAtZero: true,
                        ticks: { ...axisChrome.ticks, callback: compact }
                    },
                    y: { ...axisChrome, grid: { display: false } }
                },
                tooltip: (item) => formatMoney(item.parsed.x)
            })
        });
    }

    function baseOptions({ scales, tooltip, indexAxis }) {
        return {
            indexAxis: indexAxis || 'x',
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
                    callbacks: { label: tooltip }
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
