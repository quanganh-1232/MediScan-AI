document.addEventListener('DOMContentLoaded', () => {
    const statusMap = {
        'PENDING': 'Chờ xác nhận',
        'SCHEDULED': 'Đã đặt lịch',
        'CONFIRMED': 'Đã xác nhận',
        'CHECKED_IN': 'Đã điểm danh',
        'IN_PROGRESS': 'Đang khám',
        'COMPLETED': 'Hoàn thành',
        'CANCELLED': 'Đã hủy',
        'MISSED': 'Vắng mặt',
        'PENDING_AI': 'Đang chờ AI',
        'PENDING_DOCTOR': 'Chờ bác sĩ',
        'REJECTED': 'Bị từ chối'
    };

    function translateTextNodes(node) {
        if (node.nodeType === 3) { // Text node
            let text = node.nodeValue.trim();
            if (statusMap[text]) {
                node.nodeValue = node.nodeValue.replace(text, statusMap[text]);
            }
        } else if (node.nodeType === 1) { // Element node
            if (node.tagName !== 'SCRIPT' && node.tagName !== 'STYLE' && node.tagName !== 'NOSCRIPT') {
                for (let i = 0; i < node.childNodes.length; i++) {
                    translateTextNodes(node.childNodes[i]);
                }
            }
        }
    }

    translateTextNodes(document.body);
    
    const observer = new MutationObserver((mutations) => {
        mutations.forEach((mutation) => {
            if (mutation.addedNodes) {
                mutation.addedNodes.forEach((node) => {
                    translateTextNodes(node);
                });
            }
        });
    });
    observer.observe(document.body, { childList: true, subtree: true });
});
