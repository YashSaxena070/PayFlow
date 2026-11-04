import React, { createContext, useContext, useState, useEffect } from 'react';
import { UserSession } from '../types';
import { getStoredSession, getStoredToken, setStoredSession, clearStoredSession } from '../services/api';

interface AuthContextType {
  session: UserSession | null;
  isAuthenticated: boolean;
  login: (token: string, session: UserSession) => void;
  logout: () => void;
}

const AuthContext = createContext<AuthContextType>({
  session: null,
  isAuthenticated: false,
  login: () => {},
  logout: () => {},
});

export const AuthProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [session, setSession] = useState<UserSession | null>(() => {
    const token = getStoredToken();
    const stored = getStoredSession();
    if (token && stored) {
      return stored;
    }
    return null;
  });

  const login = (token: string, newSession: UserSession) => {
    setStoredSession(token, newSession);
    setSession(newSession);
  };

  const logout = () => {
    clearStoredSession();
    setSession(null);
  };

  return (
    <AuthContext.Provider
      value={{
        session,
        isAuthenticated: !!session,
        login,
        logout,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
};

export const useAuth = () => useContext(AuthContext);

