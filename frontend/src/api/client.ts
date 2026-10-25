import axios from "axios";

export const TOKEN_KEY = "event-registration-token";
export const AUTH_CHANGED = "auth-changed";

export function setToken(token: string | null) {
    if (token) sessionStorage.setItem(TOKEN_KEY, token);
    else sessionStorage.removeItem(TOKEN_KEY);
    window.dispatchEvent(new Event(AUTH_CHANGED));
}

const apiClient = axios.create({
    baseURL: import.meta.env.VITE_API_URL ?? "http://localhost:8080/api",
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

export function errorMessage(error: unknown): string {
    if (axios.isAxiosError(error)) return error.response?.data?.message ?? "Unable to complete the request.";
    return "Unable to complete the request.";
}

export function mediaUrl(path: string): string {
    return new URL(path, new URL(apiClient.defaults.baseURL!, window.location.origin)).toString();
}

export default apiClient;
