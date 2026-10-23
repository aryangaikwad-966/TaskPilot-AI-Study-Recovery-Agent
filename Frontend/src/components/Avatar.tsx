import React from 'react';

interface AvatarProps {
  expression?: 'neutral' | 'hyper-focus' | 'cosmic-wonder' | 'glitch-alert' | 'zen-mode';
  size?: number;
}

const Avatar: React.FC<AvatarProps> = ({ expression = 'neutral', size = 80 }) => {
  const getEyeStyle = () => {
    const baseStyle = {
      position: 'absolute' as const,
      backgroundColor: '#00d4ff',
      borderRadius: '4px',
      transition: 'all 0.4s cubic-bezier(0.4, 0, 0.2, 1)',
      boxShadow: '0 0 15px rgba(0, 212, 255, 0.8), 0 0 30px rgba(0, 212, 255, 0.4)',
    };

    switch (expression) {
      case 'neutral':
        return {
          ...baseStyle,
          width: '16px',
          height: '32px',
        };
      case 'hyper-focus':
        return {
          ...baseStyle,
          width: '10px',
          height: '45px',
          boxShadow: '0 0 20px rgba(0, 212, 255, 1), 0 0 40px rgba(0, 212, 255, 0.6)',
        };
      case 'cosmic-wonder':
        return {
          ...baseStyle,
          width: '35px',
          height: '55px',
          borderRadius: '50%',
          boxShadow: '0 0 25px rgba(0, 212, 255, 1), 0 0 50px rgba(124, 58, 237, 0.6), 0 0 75px rgba(244, 114, 182, 0.3)',
        };
      case 'glitch-alert':
        return {
          ...baseStyle,
          backgroundColor: '#ff006e',
          boxShadow: '0 0 20px rgba(255, 0, 110, 1), 0 0 40px rgba(255, 0, 110, 0.6)',
          animation: 'glitch 0.3s ease-in-out infinite',
        };
      case 'zen-mode':
        return {
          ...baseStyle,
          width: '30px',
          height: '6px',
          backgroundColor: '#8b5cf6',
          boxShadow: '0 0 15px rgba(139, 92, 246, 0.8), 0 0 30px rgba(139, 92, 246, 0.4)',
        };
      default:
        return baseStyle;
    }
  };

  const getBodyColor = () => {
    switch (expression) {
      case 'glitch-alert':
        return 'linear-gradient(135deg, #ff006e 0%, #ff3366 100%)';
      case 'zen-mode':
        return 'linear-gradient(135deg, #1e1b4b 0%, #312e81 100%)';
      case 'cosmic-wonder':
        return 'linear-gradient(135deg, #00d4ff 0%, #7c3aed 50%, #f472b6 100%)';
      default:
        return 'linear-gradient(135deg, #1e1b4b 0%, #302b63 50%, #24243e 100%)';
    }
  };

  const getBodyShadow = () => {
    switch (expression) {
      case 'glitch-alert':
        return '0 8px 24px rgba(255, 0, 110, 0.5), 0 0 40px rgba(255, 0, 110, 0.3), 0 0 60px rgba(255, 0, 110, 0.1)';
      case 'zen-mode':
        return '0 8px 24px rgba(139, 92, 246, 0.4), 0 0 40px rgba(139, 92, 246, 0.2)';
      case 'cosmic-wonder':
        return '0 8px 24px rgba(0, 212, 255, 0.5), 0 0 40px rgba(124, 58, 237, 0.3), 0 0 60px rgba(244, 114, 182, 0.2)';
      default:
        return '0 8px 24px rgba(0, 212, 255, 0.3), 0 0 40px rgba(124, 58, 237, 0.2)';
    }
  };

  return (
    <>
      <style>
        {`
          @keyframes glitch {
            0%, 100% { transform: translateX(0); }
            25% { transform: translateX(-2px); }
            75% { transform: translateX(2px); }
          }
          @keyframes float {
            0%, 100% { transform: translateY(0); }
            50% { transform: translateY(-4px); }
          }
          @keyframes pulse {
            0%, 100% { opacity: 1; }
            50% { opacity: 0.8; }
          }
          @keyframes neonPulse {
            0%, 100% { box-shadow: 0 0 20px rgba(0, 212, 255, 0.5), 0 0 40px rgba(124, 58, 237, 0.3); }
            50% { box-shadow: 0 0 30px rgba(0, 212, 255, 0.8), 0 0 60px rgba(124, 58, 237, 0.5); }
          }
        `}
      </style>
      <div
        style={{
          width: `${size}px`,
          height: `${size * 1.5}px`,
          background: getBodyColor(),
          borderRadius: `${size * 0.3}px`,
          position: 'relative',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          boxShadow: getBodyShadow(),
          transition: 'all 0.4s cubic-bezier(0.4, 0, 0.2, 1)',
          animation: expression === 'cosmic-wonder' ? 'float 3s ease-in-out infinite' : 'none',
          border: '2px solid rgba(0, 212, 255, 0.3)',
        }}
      >
        <div
          style={{
            position: 'relative',
            width: '60%',
            height: '40%',
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
          }}
        >
          <div
            style={{
              ...getEyeStyle(),
              left: '10%',
              transform: expression === 'hyper-focus' ? 'rotate(-15deg)' : 'none',
            }}
          />
          <div
            style={{
              ...getEyeStyle(),
              right: '10%',
              transform: expression === 'hyper-focus' ? 'rotate(15deg)' : 'none',
            }}
          />
        </div>
      </div>
    </>
  );
};

export default Avatar;
