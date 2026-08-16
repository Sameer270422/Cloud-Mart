import React, { createContext, useContext, useState } from 'react';

const AssistantContext = createContext(null);

export function AssistantProvider({ children }) {
  const [isOpen, setIsOpen] = useState(false);
  const [pendingMessage, setPendingMessage] = useState(null);

  const openAssistant = (message) => {
    if (message) setPendingMessage(message);
    setIsOpen(true);
  };

  const consumePendingMessage = () => {
    const msg = pendingMessage;
    setPendingMessage(null);
    return msg;
  };

  return (
    <AssistantContext.Provider value={{ isOpen, setIsOpen, openAssistant, pendingMessage, consumePendingMessage }}>
      {children}
    </AssistantContext.Provider>
  );
}

export function useAssistant() {
  return useContext(AssistantContext);
}
