import { useEffect, useRef } from 'react';

export const useFpsCounter = (enabled: boolean) => {
    const fps = useRef(0);
    const frameCount = useRef(0);
    const lastTime = useRef(0);
    const requestRef = useRef<number>();

    useEffect(() => {
        if (!enabled) return;

        lastTime.current = Date.now();
        frameCount.current = 0;

        const loop = () => {
            const now = Date.now();
            frameCount.current++;

            if (now - lastTime.current >= 1000) {
                fps.current = frameCount.current / ((now - lastTime.current) / 1000);
                frameCount.current = 0;
                lastTime.current = now;
            }

            requestRef.current = requestAnimationFrame(loop);
        };

        requestRef.current = requestAnimationFrame(loop);

        return () => {
            if (requestRef.current) cancelAnimationFrame(requestRef.current);
        };
    }, [enabled]);

    return () => fps.current;
};
