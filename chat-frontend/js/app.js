document.addEventListener('DOMContentLoaded', () => {
    const chatMessages = document.getElementById('chatMessages');
    const userInput = document.getElementById('userInput');
    const sendButton = document.getElementById('sendButton');
    const stopButton = document.getElementById('stopButton');

    let typingInterval = null; // 用于保存打字定时器
    let isTyping = false;       // 当前是否正在打字

    function formatTime(date) {
        const hours = String(date.getHours()).padStart(2, '0');
        const minutes = String(date.getMinutes()).padStart(2, '0');
        return `${hours}:${minutes}`;
    }

    function addMessage(text, isUser, isLoading = false) {
        const messageDiv = document.createElement('div');
        messageDiv.className = `message ${isUser ? 'user' : 'bot'}`;
        
        const avatarImg = document.createElement('img');
        avatarImg.className = 'message-avatar';
        avatarImg.src = isUser ? 'images/user-logo.png' : 'images/bot-logo.png';

        const bubbleDiv = document.createElement('div');
        bubbleDiv.className = 'message-bubble';

        const contentDiv = document.createElement('div');
        contentDiv.className = 'message-content';
        
        if (isLoading) {
            contentDiv.textContent = '正在思考...';
            contentDiv.classList.add('loading');
        } else {
            if (isUser) {
                contentDiv.textContent = text;
            } else {
                contentDiv.textContent = '';
                let i = 0;
                const speed = 50;
                isTyping = true;
                stopButton.style.display = 'inline-block'; // 显示停止按钮

                typingInterval = setInterval(() => {
                    if (i < text.length) {
                        contentDiv.textContent += text.charAt(i);
                        i++;
                    } else {
                        clearInterval(typingInterval);
                        isTyping = false;
                        stopButton.style.display = 'none'; // 隐藏停止按钮
                    }
                }, speed);
            }
        }

        const timeDiv = document.createElement('div');
        timeDiv.className = 'message-time';
        timeDiv.textContent = formatTime(new Date());

        bubbleDiv.appendChild(contentDiv);
        bubbleDiv.appendChild(timeDiv);
        messageDiv.appendChild(avatarImg);
        messageDiv.appendChild(bubbleDiv);

        chatMessages.appendChild(messageDiv);
        chatMessages.scrollTop = chatMessages.scrollHeight;

        return { messageDiv, contentDiv };
    }

    function stopTyping() {
        if (isTyping) {
            clearInterval(typingInterval);
            isTyping = false;
            stopButton.style.display = 'none';
        }
    }

    function sendMessage() {
        const text = userInput.value.trim();
        if (!text) return;

        addMessage(text, true);
        userInput.value = '';

        const { messageDiv: loadingMsg } = addMessage('', false, true);

        fetch('http://localhost:8080/chat/sync-test', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ message: text })
        })
        .then(res => res.json())
        .then(data => {
            loadingMsg.remove();
            const reply = data.fullResponse || '无法理解你的意思';
            addMessage(reply, false);
        })
        .catch(err => {
            console.error(err);
            loadingMsg.remove();
            addMessage('请求出错，请稍后再试。', false);
        });
    }

    sendButton.addEventListener('click', sendMessage);
    userInput.addEventListener('keypress', e => {
        if (e.key === 'Enter') sendMessage();
    });
    stopButton.addEventListener('click', stopTyping);
});