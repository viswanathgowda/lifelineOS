import { configureStore } from "@reduxjs/toolkit";
import vaultReducer from "./vaultSlice";

export const store = configureStore({
  reducer: {
    vault: vaultReducer,
  },
});

export type RootState = ReturnType<typeof store.getState>;
export type AppDispatch = typeof store.dispatch;