import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';
import { LoginPage } from './LoginPage';
import { ApiRequestError } from '../api/ApiRequestError';

const { loginMock } = vi.hoisted(() => ({ loginMock: vi.fn() }));
vi.mock('../auth/AuthContext', () => ({ useAuth: () => ({ login: loginMock }) }));

function renderPage() {
  return render(<MemoryRouter><LoginPage /></MemoryRouter>);
}

describe('LoginPage', () => {
  it('submits the credentials to the auth layer', async () => {
    loginMock.mockResolvedValueOnce(undefined);
    renderPage();

    await userEvent.type(screen.getByLabelText('Email'), 'rider@example.com');
    await userEvent.type(screen.getByLabelText('Password'), 'password123');
    await userEvent.click(screen.getByRole('button', { name: /sign in/i }));

    await waitFor(() => expect(loginMock).toHaveBeenCalledWith('rider@example.com', 'password123'));
  });

  it('shows the server error message when sign in fails', async () => {
    loginMock.mockRejectedValueOnce(new ApiRequestError('INVALID_CREDENTIALS', 'Invalid email or password'));
    renderPage();

    await userEvent.type(screen.getByLabelText('Email'), 'rider@example.com');
    await userEvent.type(screen.getByLabelText('Password'), 'wrong');
    await userEvent.click(screen.getByRole('button', { name: /sign in/i }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Invalid email or password');
  });
});
