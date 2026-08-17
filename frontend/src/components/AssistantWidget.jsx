import React, { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api.js';
import { useAuth } from '../context/AuthContext.jsx';
import { useCart } from '../context/CartContext.jsx';
import { useAssistant } from '../context/AssistantContext.jsx';

const GREETING = "Hi! I'm the CloudMart assistant. Ask me to find a product, or ask about an order you've placed.";

function ProductCardMini({ product }) {
  const { addItem } = useCart();
  return (
    <div className="assistant-product-mini">
      <div>
        <strong>{product.name}</strong>
        <span className="price" style={{ display: 'block', fontSize: '0.9rem' }}>
          ${product.price.toFixed ? product.price.toFixed(2) : product.price}
        </span>
      </div>
      <button className="secondary" onClick={() => addItem(product)}>Add</button>
    </div>
  );
}

// Unlike ProductCardMini (a suggestion the user still has to click), this is
// confirmation that the assistant already added the item - no button, just
// a receipt of what happened.
function CartAdditionMini({ item }) {
  return (
    <div className="assistant-product-mini">
      <div>
        <strong>{item.name}</strong>
        <span style={{ display: 'block', fontSize: '0.8rem', color: 'var(--success)' }}>
          ✓ Added ×{item.quantity} to cart
        </span>
      </div>
    </div>
  );
}

export default function AssistantWidget() {
  const { user } = useAuth();
  const { addItem } = useCart();
  const { isOpen, setIsOpen, pendingMessage, consumePendingMessage } = useAssistant();
  const [messages, setMessages] = useState([{ role: 'assistant', text: GREETING }]);
  const [input, setInput] = useState('');
  const [sending, setSending] = useState(false);
  const [conversationId, setConversationId] = useState(null);
  const scrollRef = useRef(null);

  useEffect(() => {
    if (scrollRef.current) {
      scrollRef.current.scrollTop = scrollRef.current.scrollHeight;
    }
  }, [messages, isOpen]);

  const send = async (text) => {
    const message = (text ?? input).trim();
    if (!message || sending || !user) return;
    setInput('');
    setMessages((prev) => [...prev, { role: 'user', text: message }]);
    setSending(true);
    try {
      const res = await api.assistantChat({ conversationId, message });
      setConversationId(res.conversationId);
      if (res.cartAdditions) {
        res.cartAdditions.forEach((item) =>
          addItem({ id: item.id, name: item.name, price: item.price }, item.quantity));
      }
      setMessages((prev) => [...prev, {
        role: 'assistant',
        text: res.reply,
        productCards: res.productCards,
        cartAdditions: res.cartAdditions,
      }]);
    } catch (e) {
      setMessages((prev) => [...prev, { role: 'assistant', text: `Sorry, I couldn't respond: ${e.message}` }]);
    } finally {
      setSending(false);
    }
  };

  useEffect(() => {
    if (isOpen && pendingMessage && user) {
      const msg = consumePendingMessage();
      send(msg);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isOpen, pendingMessage, user]);

  return (
    <>
      <button
        className="assistant-fab"
        onClick={() => setIsOpen((v) => !v)}
        aria-label={isOpen ? 'Close AI assistant' : 'Open AI assistant'}
      >
        {isOpen ? '×' : '✨'}
      </button>

      {isOpen && (
        <div className="assistant-panel">
          <div className="assistant-header">
            <span>✨ AI Shopping Assistant</span>
            <button className="icon-btn secondary" onClick={() => setIsOpen(false)} aria-label="Close">×</button>
          </div>

          <div className="assistant-messages" ref={scrollRef}>
            {messages.map((m, i) => {
              // If a product was searched and added in the same turn, don't
              // also show a clickable "Add" suggestion for it - it's
              // already in the cart, the button would be redundant.
              const addedIds = new Set((m.cartAdditions || []).map((item) => item.id));
              const suggestions = (m.productCards || []).filter((p) => !addedIds.has(p.id));
              return (
                <div key={i} className={`assistant-bubble ${m.role}`}>
                  <p>{m.text}</p>
                  {suggestions.length > 0 && (
                    <div className="assistant-product-row">
                      {suggestions.map((p) => <ProductCardMini key={p.id} product={p} />)}
                    </div>
                  )}
                  {m.cartAdditions && m.cartAdditions.length > 0 && (
                    <div className="assistant-product-row">
                      {m.cartAdditions.map((item, idx) => <CartAdditionMini key={idx} item={item} />)}
                    </div>
                  )}
                </div>
              );
            })}
            {sending && <div className="assistant-bubble assistant"><p>Thinking…</p></div>}
          </div>

          {user ? (
            <form
              className="assistant-input-row"
              onSubmit={(e) => { e.preventDefault(); send(); }}
            >
              <input
                placeholder="Ask about products or your orders..."
                value={input}
                onChange={(e) => setInput(e.target.value)}
                disabled={sending}
              />
              <button type="submit" disabled={sending || !input.trim()}>Send</button>
            </form>
          ) : (
            <div className="assistant-input-row">
              <p style={{ margin: 0, color: 'var(--text-muted)', fontSize: '0.88rem' }}>
                <Link to="/login" onClick={() => setIsOpen(false)}>Log in</Link> to chat with the assistant.
              </p>
            </div>
          )}
        </div>
      )}
    </>
  );
}
