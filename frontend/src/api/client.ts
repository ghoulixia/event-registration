import axios from "axios";

export const TOKEN_KEY = "event-registration-token";
export const AUTH_CHANGED = "auth-changed";

export function setToken(token: string | null) {
    if (token) sessionStorage.setItem(TOKEN_KEY, token);
    else sessionStorage.removeItem(TOKEN_KEY);
    window.dispatchEvent(new Event(AUTH_CHANGED));
}

const apiClient = axios.create({
    baseURL: "http://localhost:8080/api",
    headers: { "Content-Type": "application/json" },
});

apiClient.interceptors.request.use(config => {
    const token = sessionStorage.getItem(TOKEN_KEY);
    if (token) config.headers.Authorization = `Bearer ${token}`;
    return config;
});

apiClient.interceptors.response.use(response => response, error => {
    if (error.response?.status === 401 && !error.config?.url?.startsWith("/auth/")) {
        setToken(null);
        if (!['/login', '/register', '/'].includes(window.location.pathname)) {
            window.location.assign('/login');
        }
    }
    return Promise.reject(error);
});

export default apiClient;
