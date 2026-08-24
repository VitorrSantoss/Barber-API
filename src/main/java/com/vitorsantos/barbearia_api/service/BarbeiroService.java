package com.vitorsantos.barbearia_api.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.vitorsantos.barbearia_api.dto.BarbeiroRequestDTO;
import com.vitorsantos.barbearia_api.dto.BarbeiroResponseDTO;
import com.vitorsantos.barbearia_api.enums.ErrorCode;
import com.vitorsantos.barbearia_api.exception.ResourceNotFoundException;
import com.vitorsantos.barbearia_api.models.Barbeiro;
import com.vitorsantos.barbearia_api.repository.BarbeiroRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class BarbeiroService {

  private final BarbeiroRepository barbeiroRepository;

  public List<BarbeiroResponseDTO> listarBarbeiros() {
    return barbeiroRepository.findAll().stream()
        .map(BarbeiroResponseDTO::fromEntity)
        .toList();
  }

  public BarbeiroResponseDTO cadastrarBarbeiro(BarbeiroRequestDTO dto) {
    Barbeiro barbeiro = new Barbeiro();
    barbeiro.setNome(dto.nome());
    barbeiro.setAtivo(true);

    Barbeiro barbeiroSalvo = barbeiroRepository.save(barbeiro);
    return BarbeiroResponseDTO.fromEntity(barbeiroSalvo);
  }

  public void deletarBarbeiro(Long id) {
    if (!barbeiroRepository.existsById(id)) {
      throw new ResourceNotFoundException(
          "Barbeiro não encontrado com o ID: " + id,
          ErrorCode.BARBEIRO_NAO_ENCONTRADO);
    }
    barbeiroRepository.deleteById(id);
  }

}