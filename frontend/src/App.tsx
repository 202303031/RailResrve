import { Navigate, Route, Routes } from 'react-router-dom';
import { Layout } from './components/Layout';
import { ProtectedRoute } from './auth/ProtectedRoute';
import { LoginPage } from './pages/LoginPage';
import { RegisterPage } from './pages/RegisterPage';
import { SearchPage } from './pages/SearchPage';
import { SeatSelectionPage } from './pages/SeatSelectionPage';
import { PassengersPage } from './pages/PassengersPage';
import { PaymentPage } from './pages/PaymentPage';
import { MyBookingsPage } from './pages/MyBookingsPage';
import { BookingDetailPage } from './pages/BookingDetailPage';
import { AdminPage } from './pages/AdminPage';
import { NotFoundPage } from './pages/NotFoundPage';

export function App() {
  return (
    <Routes>
      <Route element={<Layout />}>
        <Route index element={<SearchPage />} />
        <Route path="login" element={<LoginPage />} />
        <Route path="register" element={<RegisterPage />} />

        <Route path="book/:scheduleId/seats" element={<ProtectedRoute><SeatSelectionPage /></ProtectedRoute>} />
        <Route path="book/:scheduleId/passengers" element={<ProtectedRoute><PassengersPage /></ProtectedRoute>} />
        <Route path="book/payment" element={<ProtectedRoute><PaymentPage /></ProtectedRoute>} />

        <Route path="bookings" element={<ProtectedRoute><MyBookingsPage /></ProtectedRoute>} />
        <Route path="bookings/:pnr" element={<ProtectedRoute><BookingDetailPage /></ProtectedRoute>} />
        <Route path="admin" element={<ProtectedRoute requireAdmin><AdminPage /></ProtectedRoute>} />

        <Route path="404" element={<NotFoundPage />} />
        <Route path="*" element={<Navigate to="/404" replace />} />
      </Route>
    </Routes>
  );
}
